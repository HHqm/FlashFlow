package com.flashflow.trade.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ShippingAddressSnapshotReaderTest {

    private ShippingAddressSnapshotReader reader;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:address_reader;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table if exists shipping_address");
        jdbcTemplate.execute("""
                create table shipping_address (
                    id bigint primary key, user_id bigint, recipient_name varchar(80), phone varchar(32),
                    province varchar(80), city varchar(80), district varchar(80), detail_address varchar(240))
                """);
        jdbcTemplate.update(
                "insert into shipping_address values (21, 7, 'Test User', '13800000000', 'Shanghai', 'Shanghai', 'Pudong', 'Test Road 1')");
        reader = new ShippingAddressSnapshotReader(jdbcTemplate);
    }

    @Test
    void readsTheOwnersSnapshotWithTheSameFormatAsTheEntity() {
        ShippingAddressSnapshot snapshot = reader.read(7L, 21L);

        assertThat(snapshot.addressId()).isEqualTo(21L);
        assertThat(snapshot.recipientName()).isEqualTo("Test User");
        assertThat(snapshot.fullAddress()).isEqualTo("Shanghai Shanghai Pudong Test Road 1");
    }

    @Test
    void anotherUsersAddressLooksMissing() {
        assertThatThrownBy(() -> reader.read(8L, 21L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ADDRESS_NOT_FOUND));
    }
}
