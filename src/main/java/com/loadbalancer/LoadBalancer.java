package com.loadbalancer;

import com.loadbalancer.algorithm.*;
import com.loadbalancer.config.Config;
import com.loadbalancer.health.HealthChecker;
import com.loadbalancer.server.Backend;
import com.loadbalancer.server.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LoadBalancer {
    
    private static final Logger logger=LoggerFactory.getLogger(LoadBalancer.class);
    private static final Config config;
    private final List<Backend>backends;
    private Listener listener;
    private HealthChecker healthChecker;
    private volatile boolean running;

    private LoadBalancer(Config config){
        this.config=config;
        this.backends=new CopyOnWriteArrayList<>();
        this.running=false;
    }

    public void start(){
        if(running){
            logger.warn("Load Balancer is already running");
            return ;
        }
        logger.info("Load Balancer is starting...")
        logger.info("Configuration Loaded");

        for(Config.BackendConfig backendConfig:config.getBackends()){
            Backend backend = new Backend(backendConfig.getHost(),backendConfig.getPort(),backendConfig.getWeight());
            backends.add(backend);
            logger.info("Registed backend:{} (weights:{})"
                backend.getAddress(),backend.getWeight()
            );
        }

        if(config.getHealthCheck() != null && config.getHealthCheck().isEnabled()){
            int intervalSeconds = parseTimeToSeconds(config.getHealthCheck().getInterval());
            healthChecker = new HealthChecker(backends, config.getHealthCheck().getPath(), config.getHealthCheck().getUnhealthyThreshold(), config.getHealthCheck().getHealthyThreshold());4

            healthChecker.start(intervalSeconds);
        }

        try{
            listener=new Listener(config.getServer().getHost,config.getServer().getPort(), backends,algorithm,config.getServer().getThreadPoolSize());

            running=true;
            new Thread(()->{
                try{
                    listener.start();
                }catch(Exception e){
                    logger.error("Listnener error",e);
                }
            })
        }catch(Exception e){
            logger.error("Failed to start load balancer",e);
            stop();
        }
    }

    public void stop(){
        if(!running){
            logger.warn("Load balancer is not running");
            return;
        }
        logger.info("Shutting down load balancer..")
        running=false;

        if(listener !=null){
            listener.stop();
        }
        if(healthChecker!=null){
            healthChecker.stop();
        }
        logger.info("Load balancer stopped");;
    }


}
