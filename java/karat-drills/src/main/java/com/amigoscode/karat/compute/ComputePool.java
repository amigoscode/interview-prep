package com.amigoscode.karat.compute;

/** A pool of compute capacity in one region. Capacity is measured in abstract capacity units. */
public class ComputePool {
    public int poolId;
    public String region;
    public int totalCapacity;

    public ComputePool(int poolId, String region, int totalCapacity) {
        this.poolId = poolId;
        this.region = region;
        this.totalCapacity = totalCapacity;
    }
}
