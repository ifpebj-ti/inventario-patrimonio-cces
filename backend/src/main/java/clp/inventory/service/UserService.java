package clp.inventory.service;

import clp.inventory.dto.GoogleUserInfo;
import clp.inventory.model.Profile;
import clp.inventory.model.Sector;
import clp.inventory.model.User;
import clp.inventory.repository.ProfileRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.security.sasl.AuthenticationException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class UserService {

    // Vazio libera qualquer domínio. Cada entrada casa com o domínio e seus subdomínios.
    @Value("${google.oauth.allowed-domains:}")
    private List<String> allowedDomains = List.of();

    private final UserRepository userRepository;
    private final SectorRepository sectorRepository;
    private final ProfileRepository profileRepository;

    @Value("${security.token.secret}")
    private String secretKey;

    public UserService(UserRepository userRepository, SectorRepository sectorRepository, ProfileRepository profileRepository) {
        this.userRepository = userRepository;
        this.sectorRepository = sectorRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * Localiza o usuário pelo e-mail do Google ou cria a conta na primeira entrada.
     * Este é o único caminho que insere em im_user.
     */
    @Transactional
    public User findOrCreateGoogleUser(GoogleUserInfo googleUser) throws AuthenticationException {
        // O Google devolve o e-mail canonicalizado em minúsculas, mas findByEmail e o
        // índice único são case-sensitive: sem normalizar, o mesmo dono viraria duas contas.
        String email = googleUser.email().trim().toLowerCase(Locale.ROOT);

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Porta de entrada: só quem ainda não tem conta passa por aqui.
        if (!isDomainAllowed(email)) {
            throw new AuthenticationException("Domain not allowed: " + email);
        }

        User user = new User();
        user.setName(googleUser.name());
        user.setEmail(email);
        user.setGoogleId(googleUser.sub());

        return userRepository.save(user);
    }

    private boolean isDomainAllowed(String email) {
        if (allowedDomains.isEmpty()) {
            return true;
        }

        String domain = email.substring(email.indexOf('@') + 1);

        // O "." antes do domínio permitido é o que impede @fakeifpe.edu.br de passar,
        // enquanto libera os subdomínios @discente. e @docente.
        return allowedDomains.stream()
                .map(allowed -> allowed.trim().toLowerCase(Locale.ROOT))
                .filter(allowed -> !allowed.isEmpty())
                .anyMatch(allowed -> domain.equals(allowed) || domain.endsWith("." + allowed));
    }

    public User getCurrentUser(String token) {
        token = token.replace("Bearer ", "");
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String uid = JWT.require(algorithm).withIssuer("inventory").build().verify(token).getSubject();

        var user = userRepository.findById(Long.parseLong(uid));

        return user.orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<User> listAllUsers() {
        return userRepository.findAll();
    }

    public User findUserById(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
    }

    @Transactional
    public User assignSector(long userId, Long sectorId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
        if (sectorId == null) {
            user.setSector(null);
        } else {
            Sector sector = sectorRepository.findById(sectorId)
                    .orElseThrow(() -> new IllegalArgumentException("Sector not found with id: " + sectorId));
            user.setSector(sector);
        }
        return userRepository.save(user);
    }

    @Transactional
    public User assignProfile(long userId, Long profileId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
        if (profileId == null) {
            user.setProfile(null);
        } else {
            Profile profile = profileRepository.findById(profileId)
                    .orElseThrow(() -> new IllegalArgumentException("Profile not found with id: " + profileId));
            user.setProfile(profile);
        }
        return userRepository.save(user);
    }

    public List<User> listUsers(Long sectorId, Long profileId) {
        if (sectorId != null) {
            return userRepository.findBySector_Id(sectorId);
        }
        if (profileId != null) {
            return userRepository.findByProfile_Id(profileId);
        }
        return userRepository.findAll();
    }

}
