# Part 3 of UWCSE's Project 3
#
# based on Lab Final from UCSC's Networking Class
# which is based on of_tutorial by James McCauley

from pox.core import core
import pox.openflow.libopenflow_01 as of
from pox.lib.addresses import IPAddr, IPAddr6, EthAddr

log = core.getLogger()

#statically allocate a routing table for hosts
#MACs used in only in part 4
IPS = {
  "h10" : ("10.0.1.10", '00:00:00:00:00:01'),
  "h20" : ("10.0.2.20", '00:00:00:00:00:02'),
  "h30" : ("10.0.3.30", '00:00:00:00:00:03'),
  "serv1" : ("10.0.4.10", '00:00:00:00:00:04'),
  "hnotrust" : ("172.16.10.100", '00:00:00:00:00:05'),
}

class Part3Controller (object):
  """
  A Connection object for that switch is passed to the __init__ function.
  """
  def __init__ (self, connection):
    print (connection.dpid)
    # Keep track of the connection to the switch so that we can
    # send it messages!
    self.connection = connection

    # This binds our PacketIn event listener
    connection.addListeners(self)
    #use the dpid to figure out what switch is being created
    if (connection.dpid == 1):
      self.s1_setup()
    elif (connection.dpid == 2):
      self.s2_setup()
    elif (connection.dpid == 3):
      self.s3_setup()
    elif (connection.dpid == 21):
      self.cores21_setup()
    elif (connection.dpid == 31):
      self.dcs31_setup()
    else:
      print ("UNKNOWN SWITCH")
      exit(1)

  def switch_set_up(self):
    msg = of.ofp_flow_mod()
     # flooding the message to all ports
    out_action = of.ofp_action_output(port = of.OFPP_FLOOD)
    # execute action
    msg.priority = 33001
    msg.match.dl_type = 0x0800
    msg.actions.append(out_action)
    # send message to switch
    self.connection.send(msg)

    msg = of.ofp_flow_mod()
     # flooding the message to all ports
    out_action = of.ofp_action_output(port = of.OFPP_FLOOD)
    # execute action
    msg.priority = 33002
    msg.match.dl_type = 0x0806
    msg.actions.append(out_action)
    # send message to switch
    self.connection.send(msg)

  def core_set_up_arp(self, name, port):
    msg = of.ofp_flow_mod()
    msg.priority = 33001
    msg.match.dl_type = 0x0806
    msg.match.nw_dst = IPAddr([name][0])
    msg.actions.append(of.ofp_action_output(port = port))
    self.connection.send(msg)
  
  def core_set_up_ip(self, name, port):
    msg = of.ofp_flow_mod()
    msg.priority = 33002
    msg.match.dl_type = 0x0800
    msg.match.nw_dst = IPAddr([name][0])
    msg.actions.append(of.ofp_action_output(port = port))
    self.connection.send(msg)

  def s1_setup(self):
    #put switch 1 rules here
    self.switch_set_up()
    

  def s2_setup(self):
    #put switch 2 rules here
    self.switch_set_up()

  def s3_setup(self):
    #put switch 3 rules here
    self.switch_set_up()

  def cores21_setup(self):
    #put core switch rules here

    # receive message from hnotrust1
    # hnotrust1 cannot send ICMP traffic to h10, h20, h30, or serv1.
    msg = of.ofp_flow_mod()
    # the source address is hnostrust
    msg.match.nw_src = IPAddr(IPS["hnotrust"][0])
    msg.priority = 33002
    # match ipv4 address
    msg.match.dl_type = 0x0800
    # ICMP protocol match
    msg.match.nw_proto = 1
    self.connection.send(msg)

    # hnotrust1 cannot send any IP traffic to serv1.
    msg = of.ofp_flow_mod()
    # the source address is hnostrust
    msg.priority = 33002
    msg.match.nw_src = IPAddr(IPS["hnotrust"][0])
    # the destination address is serv1
    msg.match.nw_dst = IPAddr(IPS["serv1"][0])
    # match ipv4 address
    msg.match.dl_type = 0x0800
    self.connection.send(msg)

    self.switch_set_up()
    self.core_set_up_arp("h10", 1)
    self.core_set_up_ip("h10", 1)
    self.core_set_up_arp("h20", 2)
    self.core_set_up_ip("h20", 2)
    self.core_set_up_arp("h30", 3)
    self.core_set_up_ip("h30", 3)
    self.core_set_up_arp("serv1", 4)
    self.core_set_up_ip("serv1", 4)
    self.core_set_up_arp("hnotrust", 5)
    self.core_set_up_ip("hnotrust", 5)



  def dcs31_setup(self):
    #put switch dcs31_setup rules here
    self.switch_set_up()

  #used in part 4 to handle individual ARP packets
  #not needed for part 3 (USE RULES!)
  #causes the switch to output packet_in on out_port
  def resend_packet(self, packet_in, out_port):
    msg = of.ofp_packet_out()
    msg.data = packet_in
    action = of.ofp_action_output(port = out_port)
    msg.actions.append(action)
    self.connection.send(msg)

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
    print ("Unhandled packet from " + str(self.connection.dpid) + ":" + packet.dump())

def launch ():
  """
  Starts the component
  """
  def start_switch (event):
    log.debug("Controlling %s" % (event.connection,))
    Part3Controller(event.connection)
  
  core.openflow.addListenerByName("ConnectionUp", start_switch)
