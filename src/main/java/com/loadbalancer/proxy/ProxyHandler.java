package com.loadbalancer.proxy;

import com.loadbalancer.algorithm.LoadBalancingAlgorithm;
import com.loadbalancer.server.Backend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;

public class ProxyHandler implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(ProxyHandler.class);
    private final Socket clientSocket;
    private final List<Backend> backends;
    private final LoadBalancingAlgorithm algorithm;
    private static final int CONNECTION_TIMEOUT=3000;
    private static final int READ_TIMEOUT=30000;
    private static final int BUFFER_SIZE=8192;

    public ProxyHandler(Socket clientSocket,List<Backend> backends,LoadBalancingAlgorithm algorithm){
        this.clientSocket=clientSocket;
        this.backends=backends;
        this.algorithm=algorithm;
    }

    @Override
    public void run(){
        try{
            handleRequest();
        } catch(SocketTimeoutException e){
            logger.warn("Request timeout:{}",e.getMessage());
        } catch(Exception e){
            logger.error("Error handling request:{}",e.getMessage());
        } finally{
            try{
                clientSocket.close();
            } catch(IOException e){
                logger.error("Error closing client socket",e);
            }
        }
    }



}
