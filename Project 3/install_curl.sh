#!/bin/bash

# Adapted from https://github.com/curl/curl/blob/master/docs/HTTP3.md

# Build openSSL
cd /home/vagrant
git clone --depth 1 -b OpenSSL_1_1_1g-quic-draft-29 https://github.com/tatsuhiro-t/openssl
cd openssl
./config enable-tls1_3 --prefix=$PWD/build
make
make install_sw

# Build nghttp3
cd ..
git clone -n https://github.com/ngtcp2/nghttp3
cd nghttp3
git checkout 056a67c
autoreconf -i
./configure --prefix=$PWD/build --enable-lib-only
make
make install

# Build ngtcp2
cd ..
git clone -n https://github.com/ngtcp2/ngtcp2
cd ngtcp2
git checkout d56b522
autoreconf -i
./configure PKG_CONFIG_PATH=$PWD/../openssl/build/lib/pkgconfig:$PWD/../nghttp3/build/lib/pkgconfig LDFLAGS="-Wl,-rpath,$PWD/../openssl/build/lib" --prefix=$PWD/build --enable-lib-only
make
make install

# Build curl
cd ..
git clone -n https://github.com/curl/curl
cd curl
git checkout b68026f7
./buildconf
LDFLAGS="-Wl,-rpath,$PWD/../openssl/build/lib" ./configure --with-ssl=$PWD/../openssl/build/ --with-nghttp3=$PWD/../nghttp3/build/ --with-ngtcp2=$PWD/../ngtcp2/build/
make