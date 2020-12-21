package part2_event_sourcing

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory

object IntroAkkaConfig extends App {

  class SimpleActor extends Actor with ActorLogging {

    override def receive: Receive = {
      case msg => log.info(msg.toString)
    }
  }

  /*
  1 - inline configure
   */
  val configString =
    """
      | akka {
      |   loglevel = "INFO" 
      | }
      |""".stripMargin

  val config = ConfigFactory.parseString(configString)
  val system = ActorSystem("ConfigurationSystem", ConfigFactory.load(config))
  val actor = system.actorOf(Props[SimpleActor], "simpleLogActor")
  actor ! "Message to remember"

  val defaultConfigFileSystem = ActorSystem("defaultConfigFileSystem")
  val defaultConfigActor = defaultConfigFileSystem.actorOf(Props[SimpleActor], "defaultConfigActor")
  defaultConfigActor ! "Remember me"

  /*
  separate config in the same file:
   */
  val specialConfig = ConfigFactory.load().getConfig("mySpecialConfig")
  val specialConfigSystem = ActorSystem("spacialConfigSystem", specialConfig)
  val specialConfigActor = defaultConfigFileSystem.actorOf(Props[SimpleActor], "spatialConfigActor")
  specialConfigActor ! "Remember me, Im special !!!"

  /*
  4 - separate config in another file:
   */
  val separateConfig = ConfigFactory.load("secretConfig.conf")
  val separateConfigSystem = ActorSystem("separateConfigSystem", separateConfig)
  val separateConfigActor = separateConfigSystem.actorOf(Props[SimpleActor], "separateConfigActor")
  separateConfigActor ! "Remember me, Im separate and special !!!" // dont see it, cause loglevel is ERROR
  println(s"separate loglevel is: ${separateConfig.getString("akka.loglevel")}")

  /*
  5- different file format
   */
  val jsonConfig = ConfigFactory.load("json/jsonConfig.json")
  println(s"json config: ${jsonConfig.getString("aJsonProps")}")
  println(s"json config: ${jsonConfig.getString("akka.loglevel")}")
}
