package akka.remote.nat

import language.postfixOps
import akka.testkit._
import com.typesafe.config.ConfigFactory
import akka.actor.{Actor, Props}
import scala.concurrent.util.duration._
import com.typesafe.config.Config
import akka.remote.testkit.{STMultiNodeSpec, MultiNodeConfig, MultiNodeSpec}
import akka.testkit._
import akka.actor.actorRef2Scala
import scala.concurrent.Await
import akka.pattern.ask
import java.util.concurrent.TimeoutException
object NatSpecConfig extends MultiNodeConfig {

commonConfig(debugConfig(on = false))

  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")

  nodeConfig(node1,setup(false, "127.0.0.1", 2552) )
  nodeConfig(node2,setup(true, "127.0.0.1", 3663))
  nodeConfig(node3,setup(true, "127.0.0.1", 6996))
  
  class SomeActor extends Actor with Serializable {
    def receive = { case "hi" => sender ! "hello"}
  }

 def setup(public: Boolean, host: String, port: Int): Config = 
     ConfigFactory.parseString("""
    akka{
      actor {
        provider = "akka.remote.RemoteActorRefProvider"
      }
      remote{
         transport = "akka.remote.netty.NettyRemoteTransport"
         public-address = %b
         netty {
          hostname = "%s"
          port = %d
        }
      }
    }
  """.format(public, host, port))
 
}

class NatSpecMultiJvmNode1 extends NatSpec
class NatSpecMultiJvmNode2 extends NatSpec
class NatSpecMultiJvmNode3 extends NatSpec

class NatSpec extends MultiNodeSpec(NatSpecConfig)
with STMultiNodeSpec with ImplicitSender with DefaultTimeout {
  import NatSpecConfig._ 

  def initialParticipants = roles.size

  override def verifySystemShutdown = true
  
  system.actorOf(Props[SomeActor], "service-hello")

  "NAT Firewall" must {
    "allow or dissalow messages properly in" in {
      
    
      runOn(node1,node2) {
        enterBarrier("start")
        enterBarrier("done")
      }
      runOn(node3) {
      enterBarrier("start")
      val actor1 = system.actorFor("akka://NatSpec@127.0.0.1:2552/user/service-hello")
      val actor2 = system.actorFor("akka://NatSpec@127.0.0.1:3663/user/service-hello")

      
      Await.result(actor1 ? "hi", 250 millis).asInstanceOf[String] must be("hello")
      

      Await.result(actor2 ? "hi", 250 millis).asInstanceOf[String] must be("hello")

      enterBarrier("done")
      }
    }
	"allow the dynamic deployment of actors across NAT" in {
	   runOn(node1,node2) {
        enterBarrier("start2")
      enterBarrier("done2")
      }
	   runOn(node3) {
	enterBarrier("start2")
	        import akka.actor.{ Props, Deploy, Address, AddressFromURIString }
            import akka.remote.RemoteScope
			
		object RemoteAkkaConnection {
              
			val ip: String = "127.0.0.1"
			val port: String = "3663"
			val ipPort = ip + ":" + port
			val actorNameString = ip.replace(".", "") + port
			val uriString = """akka://NatSpec@""" + ipPort
			
			val serviceHelloNewActorName = "service-hello" + actorNameString
		}
		import RemoteAkkaConnection._
		
		//get a reference to the actor on the remote peer.
		val originalRemoteActor = system.actorFor(uriString + """/user/service-hello""")
		
		Await.result(originalRemoteActor ? "hi", 250 millis).asInstanceOf[String] must be("hello")
		
		val address = AddressFromURIString(uriString)
		val deployedActor = system.actorOf(Props[SomeActor].withDeploy(Deploy(scope = RemoteScope(address))), serviceHelloNewActorName)
		
		Await.result(deployedActor ? "hi", 250 millis).asInstanceOf[String] must be("hello")     
		
		enterBarrier("done2")
	   }
	}
  }
}
