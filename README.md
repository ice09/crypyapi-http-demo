# Crypto Payable API (crypyAPI) xDai edition

This project implements a demo for Crypto Payments for HTTP Services.  

There are two editions: xDai and Libra. This xDai edition uses the EVM-compliant xDai side-chain (and for this demo the Sokol Testnet).

## Demo Scenarios

This main purpose of this demo is described in this blog post:

## Starting the Demo (I just want to see some jokes)

* Run Docker image in "Service Mode"
```
docker run -d -p 8889:8889 ice0nine/
```

* Run Docker image in "Client Mode"
```
docker run ice0nine/akyc-token-distribution
```

### Get SPOA (Sokol Testnet POA) from Faucet

### Send SPOA to Joke Client

## Local Demo Setup (I want to see how this works)

### Runtime Environment Setup

Depending on the scenario, there are two type of setups.

#### For Local Test

* Start Ganache on Port 8545 with mnemonic: *happy stem cram drastic uncover machine unfold year sunny feature cross ignore*
* Run Docker image
```
docker run -d -p 8888:8888 ice0nine/akyc-token-distribution
```

#### For Remote Test

Create local `application.properties` in `/var/config`
```
rpc.url=https://rinkeby.infura.io/v3/YOUR_APPLICATION_KEY
address.token=0x24eaf0fca9190c48c490eef3c0facf218cee6711
address.registry=0x10c67eFb6a3D9e7Ce14A85E9Fd498E752c38C2Bc
```

_Windows_
```
docker run -d -p 8888:8888 -e SPRING_CONFIG_LOCATION=/var/config/ -v /var/config:/var/config ice0nine/akyc-token-distribution
```

_Linux/MacOS_
```
docker run -d -p 8888:8888 -e SPRING_CONFIG_LOCATION=/var/config/ -v /var/config:/var/config ice0nine/akyc-token-distribution
```