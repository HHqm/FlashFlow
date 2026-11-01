package com.flashflow.trade.campaign;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * MySQL side of campaign stock. Each campaign's stock is split across {@code stock_buckets} rows;
 * every bucket is written by its own order writer, so commits on one hot campaign run in
 * parallel. Redis still holds a single exact counter, so admission stays globally correct and
 * the buckets only spread the write load.
 */
@Repository
public class CampaignStockRepository {

    private final JdbcTemplate jdbcTemplate;

    public CampaignStockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Splits {@code totalStock} as evenly as possible; earlier buckets take the remainder. */
    public void allocate(long campaignId, int totalStock, int buckets) {
        List<Object[]> rows = new ArrayList<>(buckets);
        for (int bucket = 0; bucket < buckets; bucket++) {
            int allocated = totalStock / buckets + (bucket < totalStock % buckets ? 1 : 0);
            rows.add(new Object[] {campaignId, bucket, allocated, allocated});
        }
        jdbcTemplate.batchUpdate("""
                insert into campaign_stock_bucket (campaign_id, bucket_no, allocated_stock, remaining_stock, version)
                values (?, ?, ?, ?, 0)
                """, rows);
    }

    /**
     * Final oversell barrier: succeeds only if this bucket still has {@code quantity} units and the
     * campaign is active. Returns the number of updated rows (0 or 1).
     */
    public int decrement(long campaignId, int bucket, int quantity) {
        return jdbcTemplate.update("""
                update campaign_stock_bucket b
                  join flash_campaign c on c.id = b.campaign_id
                   set b.remaining_stock = b.remaining_stock - ?,
                       b.version = b.version + 1
                 where b.campaign_id = ?
                   and b.bucket_no = ?
                   and b.remaining_stock >= ?
                   and c.status = 'ACTIVE'
                """, quantity, campaignId, bucket, quantity);
    }

    /** Returns stock to the bucket the order drew from; never above what that bucket was given. */
    public int increment(long campaignId, int bucket, int quantity) {
        return jdbcTemplate.update("""
                update campaign_stock_bucket
                   set remaining_stock = remaining_stock + ?,
                       version = version + 1
                 where campaign_id = ?
                   and bucket_no = ?
                   and remaining_stock + ? <= allocated_stock
                """, quantity, campaignId, bucket, quantity);
    }
}
