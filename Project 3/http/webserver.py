import SimpleHTTPServer
import SocketServer

PORT = 80

class Handler(SimpleHTTPServer.SimpleHTTPRequestHandler):
    # Disable logging DNS lookups
    def address_string(self):
        return str(self.client_address[0])

Handler = Handler
httpd = SocketServer.TCPServer(("", PORT), Handler)
print("Server1: httpd serving at port", PORT)
httpd.serve_forever()
