package com.flashflow.trade.address;

import java.util.List;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the address snapshot on the order hot path with one auto-commit statement. Going
 * through a read-only JPA transaction instead costs about seven MySQL round trips per order
 * (autocommit toggles, read-only toggles, session checks, commit), which the multi-buyer load
 * test showed to dominate order latency.
 */
@Component
public class ShippingAddressSnapshotReader {

    private static final String SQL = """
            select id, recipient_name, phone, province, city, district, detail_address
              from shipping_address
             where id = ? and user_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ShippingAddressSnapshotReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ShippingAddressSnapshot read(long userId, long addressId) {
        List<ShippingAddressSnapshot> rows = jdbcTemplate.query(
                SQL,
                (resultSet, rowNumber) -> new ShippingAddressSnapshot(
                        resultSet.getLong("id"),
                        resultSet.getString("recipient_name"),
                        resultSet.getString("phone"),
                        ShippingAddressEntity.fullAddress(
                                resultSet.getString("province"),
                                resultSet.getString("city"),
                                resultSet.getString("district"),
                                resultSet.getString("detail_address"))),
                addressId,
                userId);
        if (rows.isEmpty()) {
            // Another buyer's address is reported exactly like a missing one.
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        return rows.getFirst();
    }
}
