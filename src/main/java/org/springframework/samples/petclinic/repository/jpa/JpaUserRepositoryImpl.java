package org.springframework.samples.petclinic.repository.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.repository.UserRepository;
import org.springframework.stereotype.Repository;

@Repository
@Profile("jpa")
public class JpaUserRepositoryImpl implements UserRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public User save(User user) throws DataAccessException {
        if (this.em.find(User.class, user.getUsername()) == null) {
            this.em.persist(user);
            return user;
        } else {
            return this.em.merge(user);
        }
    }

    @Override
    public User findByUsername(String username) throws DataAccessException {
        return this.em.find(User.class, username);
    }

    @Override
    public User findByEmail(String email) throws DataAccessException {
        try {
            TypedQuery<User> query = this.em.createQuery(
                "SELECT u FROM User u WHERE u.email = :email", User.class);
            query.setParameter("email", email);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public User findByOauthIdAndOauthProvider(String oauthId, String oauthProvider) throws DataAccessException {
        try {
            TypedQuery<User> query = this.em.createQuery(
                "SELECT u FROM User u WHERE u.oauthId = :oauthId AND u.oauthProvider = :oauthProvider", User.class);
            query.setParameter("oauthId", oauthId);
            query.setParameter("oauthProvider", oauthProvider);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
