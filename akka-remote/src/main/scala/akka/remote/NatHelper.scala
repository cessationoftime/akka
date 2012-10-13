package akka.remote

import akka.actor.Address

object NatHelper {
  
 def allowNAT(natAddress: Address, settings : RemoteSettings): Boolean = {	
    import settings.PublicAddresses
	
    if (natAddress.host.isEmpty || natAddress.port.isEmpty) false //Partial addresses are never OK
	
    else PublicAddresses.nonEmpty && PublicAddresses.contains(natAddress.host.get + ":" + natAddress.port.get)	
  }
 
}