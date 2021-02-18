# Part 2 of UWCSE's Project 3
#
# based on Lab 4 from UCSC's Networking Class
# which is based on of_tutorial by James McCauley


from pox.core import core
import pox.openflow.libopenflow_01 as of

log = core.getLogger()

class Firewall (object):
  """
  A Firewall object is created for each switch that connects.
  A Connection object for that switch is passed to the __init__ function.
  """
  def __init__ (self, connection):
    # Keep track of the connection to the switch so that we can
    # send it messages!
    self.connection = connection

    # This binds our PacketIn event listener
    connection.addListeners(self)

    # add switch rules here

    # receive the message from src ip = ipv4, dst ip = ipv4
    # add flow rules
    msg_icmp = of.ofp_flow_mod()
    # set the protocol as icmp
    msg_icmp.match.nw_proto = 1
    msg_icmp.priority = 3
    # set the address as ipv4
    msg_icmp.match.dl_type = 0x0800
    # send action
    # flooding the message to all ports
    msg_icmp.actions.append(of.ofp_action_output(port = of.OFPP_FLOOD))
    # send message to switch
    self.connection.send(msg_icmp)


    msg_arp = of.ofp_flow_mod()
    # set the protocol as ARP
    msg_arp.match.dl_type = 0x0806
    # send action
    # flooding the message to all ports
    msg_arp.priority = 2
    msg_arp.actions.append(of.ofp_action_output(port = of.OFPP_FLOOD))
    # send message to switch
    self.connection.send(msg_arp)

    # the drop strategy
    msg_refuse = of.ofp_flow_mod()
    msg_refuse.match.dl_type = 0x0800
    msg_refuse.priority = 1
    self.connection.send(msg_refuse)

  def _handle_PacketIn (self, event):
    """
    Packets not handled by the router rules will be
    forwarded to this method to be handled by the controller
    """

    packet = event.parsed # This is the parsed packet data.
    if not packet.parsed:
      log.warning("Ignoring incomplete packet")
      return

    packet_in = event.ofp # The actual ofp_packet_in message.
    print ("Unhandled packet :" + str(packet.dump()))





def launch ():
  """
  Starts the component
  """
  def start_switch (event):
    log.debug("Controlling %s" % (event.connection,))
    Firewall(event.connection)
  core.openflow.addListenerByName("ConnectionUp", start_switch)