# Crypto Payable API (crypyAPI) xDai edition

This project implements a demo for Crypto Payments for HTTP Services.  

There are two editions: xDai and Libra. This xDai edition uses the EVM-compliant xDai side-chain (and for this demo the Sokol Testnet).

## Demo Scenarios

This main purpose of this demo is described in this blog post: http://blockchainers.tech/pay-robots-with-crypto-money/

There are two demo scenarios, a showcase sample and a deep dive sample for developers.

## Starting the Demo (I just want to see some jokes)

* Run Docker image in "Service Mode"
```
docker run -d -p 8889:8889 --name crypyapi-http-demo-service ice0nine/crypyapi-http-demo:ethereum
```

This container is started in demo mode, you will see just the container-id output.

* Run Docker image in "Client Mode"
```
docker run --name crypyapi-http-demo-client -e mode=client ice0nine/crypyapi-http-demo:ethereum
```

After the initial start-up, the application will wait for you sending some Sokol POA (1 SPOA is enough).  

### Get SPOA (Sokol Testnet POA) from Faucet

First, you will have to have a wallet installed and received SPOA on the Sokol Testnet: https://www.poa.network/for-developers/getting-tokens-for-tests/sokol-testnet-faucet

### Send SPOA to Joke Client

Use the wallet and send 1 SPOA to the address displayed by the client. Afterwards, you should see jokes until the SPOA is spent.

## Local Demo Setup (I want to see how this works)

### Runtime Environment Setup

#### Payment Receiver (Joke Service)

You can import the project into IntelliJ and start the server as a spring boot application (`ERC20Application`)

There is an application configuration (`crypyapi.properties`) which should be fine for the demo. However, you can tweak this to you needs.
```
ethereum.rpc.url=https://sokol.poa.network
```

#### Payment Sender (Joke Client)

After the server started, you can run the application again with the environment variable `mode` set to `client`.  
The client will start and display the address it waits for SPOA to transfer it to the Payment Receiver.

You can then proceed with step *Get SPOA (Sokol Testnet POA) from Faucet* above.