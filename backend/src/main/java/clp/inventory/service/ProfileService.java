package clp.inventory.service;

import clp.inventory.dto.ProfileDto;
import clp.inventory.model.Profile;
import clp.inventory.repository.ProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ProfileService {

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional
    public Profile createProfile(ProfileDto dto) {
        validate(dto);
        if (profileRepository.existsByName(dto.name())) {
            throw new IllegalArgumentException("Profile with name '" + dto.name() + "' already exists");
        }
        Profile profile = new Profile(dto.name().trim(), dto.description());
        return profileRepository.save(profile);
    }

    @Transactional
    public Profile updateProfile(long id, ProfileDto dto) {
        validate(dto);
        Profile profile = findProfileById(id);
        if (profileRepository.existsByNameAndIdNot(dto.name(), id)) {
            throw new IllegalArgumentException("Profile with name '" + dto.name() + "' already exists");
        }
        profile.setName(dto.name().trim());
        profile.setDescription(dto.description());
        return profileRepository.save(profile);
    }

    @Transactional
    public void deleteProfile(long id) {
        if (!profileRepository.existsById(id)) {
            throw new NoSuchElementException("Profile not found with id: " + id);
        }
        try {
            profileRepository.deleteById(id);
            profileRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Profile is in use and cannot be deleted");
        }
    }

    public Profile findProfileById(long id) {
        return profileRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Profile not found with id: " + id));
    }

    public List<Profile> listAllProfiles() {
        return profileRepository.findAll();
    }

    private void validate(ProfileDto dto) {
        if (dto.name() == null || dto.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Profile name cannot be null or empty");
        }
        if (dto.name().trim().length() > 100) {
            throw new IllegalArgumentException("Profile name cannot exceed 100 characters");
        }
        if (dto.description() != null && dto.description().length() > 255) {
            throw new IllegalArgumentException("Profile description cannot exceed 255 characters");
        }
    }
}
