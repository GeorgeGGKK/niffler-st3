package guru.qa.niffler.db.dao.impl;

import guru.qa.niffler.db.jdbc.DataSourceProvider;
import guru.qa.niffler.db.ServiceDB;
import guru.qa.niffler.db.dao.AuthUserDAO;
import guru.qa.niffler.db.dao.UserDataUserDAO;
import guru.qa.niffler.db.model.userdata.UserDataEntity;
import guru.qa.niffler.db.springjdbc.UserDataEntityRowMapper;
import guru.qa.niffler.db.springjdbc.UserEntityRowMapper;
import guru.qa.niffler.db.model.auth.Authority;
import guru.qa.niffler.db.model.CurrencyValues;
import guru.qa.niffler.db.model.auth.UserEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class AuthUserDAOSpringJdbc implements AuthUserDAO, UserDataUserDAO {

    private final TransactionTemplate authTtpl;
    private final TransactionTemplate userdataTtpl;
    private final JdbcTemplate authJdbcTemplate;
    private final JdbcTemplate userdataJdbcTemplate;

    public AuthUserDAOSpringJdbc() {
        JdbcTransactionManager authTm = new JdbcTransactionManager(
                DataSourceProvider.INSTANCE.getDataSource(ServiceDB.AUTH));
        JdbcTransactionManager userdataTm = new JdbcTransactionManager(
                DataSourceProvider.INSTANCE.getDataSource(ServiceDB.USERDATA));

        this.authTtpl = new TransactionTemplate(authTm);
        this.userdataTtpl = new TransactionTemplate(userdataTm);
        this.authJdbcTemplate = new JdbcTemplate(authTm.getDataSource());
        this.userdataJdbcTemplate = new JdbcTemplate(userdataTm.getDataSource());
    }

    @Override
    @SuppressWarnings("unchecked")
    public int createUser(UserEntity user) {
        return authTtpl.execute(status -> {
            KeyHolder kh = new GeneratedKeyHolder();

            authJdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement("INSERT INTO users (username, password, enabled, account_non_expired, account_non_locked, credentials_non_expired) " +
                        "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, user.getUsername());
                ps.setString(2, pe.encode(user.getPassword()));
                ps.setBoolean(3, user.getEnabled());
                ps.setBoolean(4, user.getAccountNonExpired());
                ps.setBoolean(5, user.getAccountNonLocked());
                ps.setBoolean(6, user.getCredentialsNonExpired());
                return ps;
            }, kh);
            final UUID userId = (UUID) kh.getKeyList().get(0).get("id");
            authJdbcTemplate.batchUpdate("INSERT INTO authorities (user_id, authority) VALUES (?, ?)", new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setObject(1, userId);
                    ps.setObject(2, Authority.values()[i].name());
                }

                @Override
                public int getBatchSize() {
                    return Authority.values().length;
                }
            });
            return 1;
        });
    }

    @Override
    public UserEntity getUser(UUID userId) {
        return authJdbcTemplate.queryForObject(
                "SELECT * FROM users u " +
                "JOIN authorities a ON u.id = a.user_id " +
                        "WHERE u.id = ?",
                UserEntityRowMapper.instance,
                userId
        );
    }

    @Override
    public void updateUser(UserEntity user) {
        authJdbcTemplate.update(
                        "UPDATE users SET " +
                                "password = ?," +
                                "enabled = ?," +
                                "account_non_expired = ?," +
                                "account_non_locked = ?," +
                                "credentials_non_expired = ? " +
                                "WHERE id = ? ",
                                pe.encode(user.getPassword()),
                                user.getEnabled(),
                                user.getAccountNonExpired(),
                                user.getAccountNonLocked(),
                                user.getCredentialsNonExpired(),
                                user.getId());
    }

    @Override
    public void deleteUserById(UUID userId) {
         authTtpl.executeWithoutResult(status -> {
            authJdbcTemplate.update("DELETE FROM authorities WHERE user_id = ?", userId);
            authJdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        });
    }


    @Override
    public int createUserInUserData(UserDataEntity user) {
        return userdataJdbcTemplate.update(
                "INSERT INTO users (username, currency) VALUES (?, ?)",
                user.getUsername(),
                CurrencyValues.RUB.name()
        );
    }

    @Override
    public UserDataEntity getUserInUserDataByUsername(String username) {
        return userdataJdbcTemplate.queryForObject("SELECT * FROM users WHERE username = ?",
                UserDataEntityRowMapper.instance,
                username);
    }

    @Override
    public void updateUserInUserData(UserDataEntity userDe) {
        userdataJdbcTemplate.update("UPDATE users SET " +
                "currency = ?," +
                "firstname = ?," +
                "surname = ?," +
                "photo = ?," +
                "WHERE id = ? ",
                userDe.getCurrency(),
                userDe.getFirstname(),
                userDe.getSurname(),
                userDe.getPhoto(),
                userDe.getId());
    }

    @Override
    public void deleteUserByUsernameInUserData(String username) {
        userdataJdbcTemplate.update("DELETE FROM users WHERE username = ?", username);
    }
}
