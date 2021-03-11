# Part 3 of UWCSE's Project 3
#
# based on Lab Final from UCSC's Networking Class
# which is based on of_tutorial by James McCauley
 
from pox.core import core
import pox.openflow.libopenflow_01 as of
from pox.lib.addresses import IPAddr, IPAddr6, EthAddr
from pox.lib.packet import arp
from pox.lib.packet import ethernet
 
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
 
ip_learn_set = set() # set of ips seen for core switch
 
class Part4Controller (object):
  """
 A Connection object for that switch is passed to the __init__ function.
 """
  def __init__ (self, connection):
    print (connection.dpid)
    # Keep track of the connection to the switch so that we can
    # send it messages!
    self.connection = connection
 
    # This binds our PacketIn event listener
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
    # flood arp packets
    msg = of.ofp_flow_mod()
    msg.priority = 33001
    msg.match.dl_type = 0x0800
    msg.actions.append(of.ofp_action_output(port = of.OFPP_FLOOD))
    self.connection.send(msg)
    # flood ip packets
    msg.priority = 33002
    msg.match.dl_type = 0x0806
    msg.match.nw_proto = None
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
 
  def dcs31_setup(self):
    #put datacenter switch rules here
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
    core_mac_addr = EthAddr("de:ed:be:ef:ca:fe")
    port_num = packet_in.in_port
    mac_addr_src = EthAddr(packet.src)
    # packet receives ARP request needs to respond an ARP message
    if packet.type == packet.ARP_TYPE:
        arp_packet = packet.find('arp')
        ip_addr_host = arp_packet.protosrc
       
        if not ip_addr_host in ip_learn_set:
            # cores21 install a rule on the address and port
            msg = of.ofp_flow_mod()
            msg.priority = 33001
            msg.match.nw_dst = IPAddr(ip_addr_host)
            # arp translate from ip to mac
            msg.actions.append(of.ofp_action_dl_addr.set_dst(mac_addr_src))
            msg.actions.append(of.ofp_action_output(port = port_num))
            msg.match.dl_type = 0x0806
            self.connection.send(msg)
            # ip address
            msg.priority = 33002
       
            msg.match.dl_type = 0x0800
            self.connection.send(msg)
           
            ip_learn_set.add(ip_addr_host)
       
        # reply to ARP messages with arbitrary MAC addr to assume control
        arp_reply = arp()
        arp_reply.hwsrc = core_mac_addr
        arp_reply.hwdst = packet.src
        arp_reply.opcode = arp.REPLY
        arp_reply.protosrc = arp_packet.protodst
        arp_reply.protodst = packet.payload.protosrc
        # ethernet wrap packets
        ether = ethernet()
        ether.type = ethernet.ARP_TYPE
        ether.src = core_mac_addr
        ether.dst = packet.src
        # resend the packet
        ether.payload = arp_reply        
        self.resend_packet(ether, port_num)
 
    else: # if packet isn't an ARP packet do nothing
        print ("Unhandled packet from " + str(self.connection.dpid) + ":" + packet.dump())
 
def launch ():
  """
 Starts the component
 """
  def start_switch (event):
    log.debug("Controlling %s" % (event.connection,))
    Part4Controller(event.connection)
  core.openflow.addListenerByName("ConnectionUp", start_switch)