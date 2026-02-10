package com.loadbalancer.health;

public class HealthCheckResult {
    
    private final boolean healthy;

    private final long responseTime;

    private final String message;

    public HealthCheckResult(boolean healthy,long responseTime,String message){
        this.healthy=healthy;
        this.responseTime=responseTime;
        this.message=message;
    }
    public boolean isHealthy(){return healthy;}
    public long getResponseTime() {return responseTime;}
    public String getMessage() {return message;}
}
