#!/bin/bash

cd /home/vagrant
git clone -n https://github.com/aiortc/aioquic
cd aioquic
git checkout c99b43f
cp /home/vagrant/project3/http/index.html /home/vagrant/aioquic/examples/templates/index.html
sudo apt install libssl-dev python3-dev
sudo pip3 install -e .
sudo pip3 install aiofiles asgiref dnslib httpbin starlette wsproto