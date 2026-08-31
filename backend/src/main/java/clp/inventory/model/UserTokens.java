package clp.inventory.model;

import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Entity
@Table(name = "im_user_tokens")
public class UserTokens {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column
    private String token;

    @Column
    private TokenType tokenType;

    @Column(nullable = false, updatable = false, insertable = false)
    private LocalDateTime expiration;

    @OneToOne
    @JoinColumn(name = "id_user", nullable = false)
    private User user;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public void setTokenType(TokenType tokenType) {
        this.tokenType = tokenType;
    }

    public LocalDateTime getExpiration() {
        return expiration;
    }

    public void setExpiration(LocalDateTime expiration) {
        this.expiration = expiration;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public UserTokens(String token, TokenType tokenType, User user) {
        this.token = token;
        this.tokenType = tokenType;
        this.user = user;
    }

    public UserTokens() {

    }
}
