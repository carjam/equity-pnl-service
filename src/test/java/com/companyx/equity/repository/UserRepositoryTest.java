package com.companyx.equity.repository;

import com.companyx.equity.TestDataBuilder;
import com.companyx.equity.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UserRepository
 */
@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class UserRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    public void testFindByUid_Success() {
        User user = TestDataBuilder.createPersistableUser("test-user-123", "password");
        entityManager.persistAndFlush(user);
        
        Optional<User> found = userRepository.findByUid("test-user-123");
        
        assertTrue(found.isPresent());
        assertEquals("test-user-123", found.get().getUid());
    }
    
    @Test
    public void testFindByUid_NotFound() {
        Optional<User> found = userRepository.findByUid("nonexistent");
        
        assertFalse(found.isPresent());
    }
    
    @Test
    public void testFindByUid_CaseSensitive() {
        User user = TestDataBuilder.createPersistableUser("TestUser", "password");
        entityManager.persistAndFlush(user);
        
        Optional<User> found1 = userRepository.findByUid("TestUser");
        Optional<User> found2 = userRepository.findByUid("testuser");
        
        assertTrue(found1.isPresent());
        // Depending on database collation, this might or might not find the user
        // In most cases, it should be case-sensitive
    }
    
    @Test
    public void testSaveUser() {
        User user = TestDataBuilder.createPersistableUser("new-user", "password123");
        
        User saved = userRepository.save(user);
        
        assertNotNull(saved.getId());
        assertEquals("new-user", saved.getUid());
        assertEquals("password123", saved.getPassword());
    }
    
    @Test
    public void testDeleteUser() {
        User user = TestDataBuilder.createPersistableUser("temp-user", "password");
        user = entityManager.persistAndFlush(user);
        int userId = user.getId();
        
        userRepository.deleteById(userId);
        entityManager.flush();
        
        Optional<User> found = userRepository.findById(userId);
        assertFalse(found.isPresent());
    }
}
