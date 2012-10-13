/**
 *  Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.remote.nat

import language.postfixOps
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.pattern.ask
import akka.remote.testkit.{ STMultiNodeSpec, MultiNodeConfig, MultiNodeSpec }
import akka.testkit._
import akka.actor.Terminated
import scala.concurrent.util.duration._
import com.typesafe.config.ConfigFactory
import akka.remote.RemoteActorRef
import akka.actor.ActorRef
object NewRemoteActorMultiJvmSpec extends MultiNodeConfig {
import NatSpecConfig.setup
  class SomeActor extends Actor {
    def receive = {
      case "identify" ⇒ sender ! self
    }
  }

  commonConfig(debugConfig(on = false).withFallback(
    ConfigFactory.parseString("akka.remote.log-remote-lifecycle-events = off")))

  val master = role("master")
  val slave = role("slave")
  nodeConfig(slave,setup(true, "127.0.0.1", 3663))
  nodeConfig(master,setup(true, "127.0.0.1", 6996))

  deployOn(master, """
    /service-hello.remote = "akka://NewRemoteActorSpec@127.0.0.1:3663"
    /service-hello3.remote = "akka://NewRemoteActorSpec@127.0.0.1:3663"
    """)

  deployOnAll("""/service-hello2.remote = "akka://NewRemoteActorSpec@127.0.0.1:3663" """)
}

class NewRemoteActorMultiJvmMaster extends NewRemoteActorSpec
class NewRemoteActorMultiJvmSlave extends NewRemoteActorSpec

class NewRemoteActorSpec extends MultiNodeSpec(NewRemoteActorMultiJvmSpec)
  with STMultiNodeSpec with ImplicitSender with DefaultTimeout {
  import NewRemoteActorMultiJvmSpec._

  def initialParticipants = roles.size

  // ensure that system shutdown is successful
  override def verifySystemShutdown = true
  
  val slavesNAT = akka.actor.AddressFromURIString("akka://NewRemoteActorSpec@127.0.0.1:3663")
  
  "A new NATed remote actor" must {
    "be locally instantiated on a remote node and be able to communicate through its RemoteActorRef" taggedAs LongRunningTest in {

      runOn(master) {
        val actor = system.actorOf(Props[SomeActor], "service-hello")
        actor.isInstanceOf[RemoteActorRef] must be(true)
        
        actor.path.address must be(slavesNAT)
        actor.path.address must be(node(slave).address)
        
        val slaveAddress = testConductor.getAddressFor(slave).await

        actor ! "identify"
       val expected = expectMsgType[ActorRef].path.address
       expected must equal(slaveAddress)
       expected must equal(slavesNAT)
      }

      enterBarrier("done")
    }

    "be locally instantiated on a remote node and be able to communicate through its RemoteActorRef (with deployOnAll)" taggedAs LongRunningTest in {

      runOn(master) {
        val actor = system.actorOf(Props[SomeActor], "service-hello2")
        actor.isInstanceOf[RemoteActorRef] must be(true)
        
        actor.path.address must be(slavesNAT)
        actor.path.address must be(node(slave).address)

        val slaveAddress = testConductor.getAddressFor(slave).await
        actor ! "identify"
        val expected = expectMsgType[ActorRef].path.address
       expected must equal(slaveAddress)
       expected must equal(slavesNAT)
      }

      enterBarrier("done")
    }

    "be able to shutdown system when using remote deployed actor" taggedAs LongRunningTest in within(10 seconds) {
      runOn(master) {
        val actor = system.actorOf(Props[SomeActor], "service-hello3")
        actor.isInstanceOf[RemoteActorRef] must be(true)
         actor.path.address must be(slavesNAT)
        actor.path.address must be(node(slave).address)
        watch(actor)

        enterBarrier("deployed")

        // master system is supposed to be shutdown after slave
        // this should be triggered by slave system shutdown
        expectMsgPF(remaining) { 
          case Terminated(`actor`) ⇒ true;
          }
      }

      runOn(slave) {
        enterBarrier("deployed")
      }

      // Important that this is the last test.
      // It must not be any barriers here.
      // verifySystemShutdown = true will ensure that system shutdown is successful
    }
  }
}
