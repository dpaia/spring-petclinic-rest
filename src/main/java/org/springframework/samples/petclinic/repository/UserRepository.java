package org.springframework.samples.petclinic.repository;

import org.springframework.dao.DataAccessException;
import org.springframework.samples.petclinic.model.User;

public interface UserRepository {

    User save(User user) throws DataAccessException;
    
    User findByUsername(String username) throws DataAccessException;
    
    User findByEmail(String email) throws DataAccessException;
    
    User findByOauthIdAndOauthProvider(String oauthId, String oauthProvider) throws DataAccessException;
}
