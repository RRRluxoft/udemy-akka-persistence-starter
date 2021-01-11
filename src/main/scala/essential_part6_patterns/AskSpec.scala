package essential_part6_patterns

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
// step 1
import akka.pattern.ask
import akka.pattern.pipe

import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike

class AskSpec extends TestKit(ActorSystem("AskSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  import AskSpec._
  "An authenticator" should {
    handleAuth(Props[AuthManager])
  }

  "An authenticator piped" should {
    handleAuth(Props[PipedAuthManager])
  }

  def handleAuth(props: Props): Unit = {
    import AuthManager._
    "fail to authenticate a NON-registered user" in {
      val authManager = system.actorOf(props)
      authManager ! Authenticate("spy", "goto")
      expectMsg(AuthFailure(AUTH_FAILURE_NOT_FOUND))
    }

    "fail to authenticate if invalid password" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("boss", "return")
      authManager ! Authenticate("boss", "dontGo")
      expectMsg(AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT))
    }

    "success to authenticate if invalid password" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("boss", "return")
      authManager ! Authenticate("boss", "return")
      expectMsg(AuthSuccess)
    }
  }

}

object AskSpec {

  //

  case class Read(key: String)
  case class Write(key: String, value: String)

  class KVActor extends Actor with ActorLogging {
    override def receive: Receive = online(Map())

    def online(kv: Map[String, String]): Receive = {
      case Read(key) =>
        log.info(s"Reading key $key")
        sender() ! kv.get(key)
      case Write(key, value) =>
        log.info(s"Writing the value $value for the key $key")
        context.become(online(kv + (key -> value)))
    }
  }

  // user authenticator actor:
  case class RegisterUser(username: String, pwd: String)
  case class Authenticate(username: String, pwd: String)
  case class AuthFailure(msg: String)
  case object AuthSuccess

  object AuthManager {
    val AUTH_FAILURE_NOT_FOUND = "username nor found"
    val AUTH_FAILURE_PASSWORD_INCORRECT = "pwd incorrect"
    val AUTH_FAILURE_SYSTEM = "system error"
  }

  class AuthManager extends Actor with ActorLogging {

    // step 2 - logistic
    implicit val timeout: Timeout = Timeout(1 second)
    implicit val ec: ExecutionContext = context.dispatcher

    protected val authDB: ActorRef = context.actorOf(Props[KVActor], "authDBActor")

    override def receive: Receive = {
      case RegisterUser(name, pwd) =>
        log.info(s"User $name is trying to register")
        authDB ! Write(name, pwd)
      case Authenticate(name, pwd) => handleAuth(name, pwd)
    }

    def handleAuth(name: String, pwd: String): Unit = {
      log.info(s"User $name is trying to authenticate")
      val originalSender = sender()
      // step 3 - ask the actor
      val future = authDB ? Read(name)

      import AuthManager._
      // step 4 - handle the future
      future.onComplete {
        // step 5 - most important:
        // NEVER call methods on the actor instance OR access mutable state in onComplete !!!
        case Success(None) => originalSender ! AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Success(Some(dbPassword)) =>
          if (dbPassword == pwd) originalSender ! AuthSuccess
          else originalSender ! AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
        case Failure(_) => originalSender ! AuthFailure(AUTH_FAILURE_SYSTEM)
      }
    }
  }

  class PipedAuthManager extends AuthManager {
    import AuthManager._

    override def handleAuth(name: String, pwd: String): Unit = {
      val future: Future[Any] = authDB ? Read(name)
      val pwdFuture: Future[Option[String]] = future.mapTo[Option[String]]
      val responseFuture: Future[Product] = pwdFuture.map {
        case None => AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Some(dbPwd) =>
          if (dbPwd == pwd) AuthSuccess
          else AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
      }
      // !!! when future complete, send the response to the actor Ref in the arg list:
      responseFuture.pipeTo(sender())
    }
  }

}
