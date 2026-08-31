package clp.inventory.service;

import clp.inventory.dto.UserDto;
import clp.inventory.model.TokenType;
import clp.inventory.model.User;
import clp.inventory.model.UserTokens;
import clp.inventory.repository.UserRepository;
import clp.inventory.repository.UserTokensRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final UserTokensRepository userTokensRepository;

    @Value("${security.token.secret}")
    private String secretKey;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            UserTokensRepository userTokensRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.userTokensRepository = userTokensRepository;
    }

    public User createUser(UserDto userDto) {

        userRepository.findByEmail(userDto.email())
                .ifPresent(user -> {
                    throw new RuntimeException("User with email " + userDto.email() + " already exists");
                });

        var password = passwordEncoder.encode(userDto.password());

        User user = new User(
                userDto.name(),
                userDto.email(),
                password,
                false,
                userDto.telephone()
        );

        String token = UUID.randomUUID().toString();
        User savedUser = userRepository.save(user);

        UserTokens userTokens = new UserTokens(token, TokenType.VERIFICATION, savedUser);
        userTokensRepository.save(userTokens);
        emailService.sendVerificationEmail(savedUser.getEmail(), token);

        return savedUser;
    }

    public User verifyUser(String token) {
        UserTokens userTokens = userTokensRepository.findByToken(token).orElse(null);
        if (userTokens == null) {
            System.out.print("Token not found");
            throw new RuntimeException("User not found");
        }

        if (userTokens.getExpiration().isBefore(LocalDateTime.now())) {
            System.out.print("Token is expired");
            System.out.print(userTokens.getExpiration().isAfter(LocalDateTime.now()));
            throw new RuntimeException("Expired token");
        }

        User user = userRepository.findUserById(userTokens.getUser().getId()).orElse(null);
        assert user != null : "User not found";
        user.setVerified(true);
        return userRepository.save(user);
    }

    @Transactional
    public void askPasswordResetEmail(String email) {
        String token = UUID.randomUUID().toString();
        userRepository.findByEmail(email).ifPresent(user -> {
        });
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            emailService.sendPasswordResetEmail(user.getEmail(), token);
            UserTokens userTokens = new UserTokens(token, TokenType.RESETPASSWORD, user);
            userTokensRepository.save(userTokens);
            return;
        }
        throw new RuntimeException("User not found");
    }

    @Transactional
    public User resetPassword(String token, String newPassword) {
        UserTokens userToken = userTokensRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token de redefinição inválido ou já utilizado."));

        if (userToken.getExpiration().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expirado. Por favor, solicite um novo link.");
        }

        User user = userToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        User savedUser = userRepository.save(user);
        // Token de uso único: precisa ser removido após a troca para impedir reutilização do link.
        userTokensRepository.delete(userToken);

        return savedUser;
    }

    public void sendEmailVerification(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String token = UUID.randomUUID().toString();
            UserTokens userTokens = new UserTokens(token, TokenType.VERIFICATION, user);
            userTokensRepository.save(userTokens);
            emailService.sendVerificationEmail(user.getEmail(), token);
            return;
        }
        throw new RuntimeException("User not found");
    }

    public User getCurrentUser(String token) {
        token = token.replace("Bearer ", "");
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String uid = JWT.require(algorithm).withIssuer("inventory").build().verify(token).getSubject();

        var user = userRepository.findById(Long.parseLong(uid));

        return user.orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<User> listAllUsers() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = (String) authentication.getPrincipal();

        return userRepository.findAll();
    }

    public User findUserById(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
    }

}
