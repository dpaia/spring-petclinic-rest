package org.springframework.samples.petclinic.repository.jdbc;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.samples.petclinic.model.Role;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.repository.UserRepository;
import org.springframework.stereotype.Repository;

@Repository
@Profile("jdbc")
public class JdbcUserRepositoryImpl implements UserRepository {

    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private SimpleJdbcInsert insertUser;

    @Autowired
    public JdbcUserRepositoryImpl(DataSource dataSource) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.insertUser = new SimpleJdbcInsert(dataSource).withTableName("users");
    }

    @Override
    public User save(User user) throws DataAccessException {

        BeanPropertySqlParameterSource parameterSource = new BeanPropertySqlParameterSource(user);

        try {
            getByUsername(user.getUsername());
            this.namedParameterJdbcTemplate.update(
                "UPDATE users SET password=:password, enabled=:enabled, email=:email, first_name=:firstName, last_name=:lastName, oauth_provider=:oauthProvider, oauth_id=:oauthId, picture_url=:pictureUrl WHERE username=:username",
                parameterSource);
        } catch (EmptyResultDataAccessException e) {
            this.insertUser.execute(parameterSource);
        } finally {
            updateUserRoles(user);
        }
        return user;
    }

    @Override
    public User findByUsername(String username) throws DataAccessException {
        return getByUsername(username);
    }

    @Override
    public User findByEmail(String email) throws DataAccessException {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("email", email);
            return this.namedParameterJdbcTemplate.queryForObject("SELECT * FROM users WHERE email=:email",
                params, BeanPropertyRowMapper.newInstance(User.class));
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public User findByOauthIdAndOauthProvider(String oauthId, String oauthProvider) throws DataAccessException {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("oauthId", oauthId);
            params.put("oauthProvider", oauthProvider);
            return this.namedParameterJdbcTemplate.queryForObject(
                "SELECT * FROM users WHERE oauth_id=:oauthId AND oauth_provider=:oauthProvider",
                params, BeanPropertyRowMapper.newInstance(User.class));
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private User getByUsername(String username) {

        Map<String, Object> params = new HashMap<>();
        params.put("username", username);
        return this.namedParameterJdbcTemplate.queryForObject("SELECT * FROM users WHERE username=:username",
            params, BeanPropertyRowMapper.newInstance(User.class));
    }

    private void updateUserRoles(User user) {
        Map<String, Object> params = new HashMap<>();
        params.put("username", user.getUsername());
        this.namedParameterJdbcTemplate.update("DELETE FROM roles WHERE username=:username", params);
        for (Role role : user.getRoles()) {
            params.put("role", role.getName());
            if (role.getName() != null) {
                this.namedParameterJdbcTemplate.update("INSERT INTO roles(username, role) VALUES (:username, :role)", params);
            }
        }
    }
}
