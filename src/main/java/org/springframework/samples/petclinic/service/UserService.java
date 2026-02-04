package org.springframework.samples.petclinic.service;

import org.springframework.samples.petclinic.model.User;

public interface UserService {

    User saveUser(User user);
    
    User findByUsername(String username);
    
    User findByEmail(String email);
    
    User findByOauthIdAndOauthProvider(String oauthId, String oauthProvider);
}
