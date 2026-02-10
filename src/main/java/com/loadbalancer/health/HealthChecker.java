package com.loadbalancer.health;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.loadbalancer.server.Backend;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HealthChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthChecker.class);
    private final List<Backend> backends;
    private final String healthCheckPath;
    private final int unhealthyThreshold;
    private final int healthyThreshold;
    private final ScheduledExecutorService scheduler;
    private final CloseableHttpClient httpClient;

    public HealthChecker(List<Backend> backends,String healthCheckPath,int unhealthyThreshold,int healthyThreshold){
        this.backends=backends;
        this.healthCheckPath=healthCheckPath;
        this.unhealthyThreshold=unhealthyThreshold;
        this.healthyThreshold=healthyThreshold;

        this.scheduler=Executor.newScheduledThreadPool(1);
        this.httpClient=HttpClients.createDefault();
    }

    public void start(long intervalSeconds){
        scheduler.scheduleAtFixedRate(this::checkAllBackends,0,intervalSeconds,TimeUnit.SECONDS);
        logger.info("Health Checker started (interval:{}s",intervalSeconds);
    }

    private void checkAllBackends(){
        int healthyCount =0;
        for(Backend backend : backends){
            HealthCheckResult result = checkBackend(backend);
            updateBackendHealth(backend,result);

            if(backend.isHealthy()){
                return healthyCount++;
            }
        }
        logger.debug("Health Check completed : {}/{} backends healthy",healthyCount,backends.size());
    }

    private HealthCheckResult checkBackend(Backend backend){
        String url= String.format("",backend.getHost(),backend.getPort(),healthCheckPath);

        long startTime=System.currentTimeMillis();

        try{
            HttpGet request = new HttpGet(url);
            long responseTime = System.currentTimeMillis()-startTime;
            int statusCode = response.getCode();
            EntityUtils.consume(response.getEntity());
            if(statusCode == 200){
                return new HealthCheckResult(true, responseTime, "OK");
            }else{
                return new HealthCheckResult(false, responseTime,"Status:"+statusCode);
            }
        }catch(Exception e){
            long responseTime = System.currentTimeMillis()-startTime;
            return new HealthCheckResult(false, responseTime, e.getMessage());
        }
    }
}
