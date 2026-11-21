package com.departementemployee.users.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.departementemployee.users.entities.Role;
import com.departementemployee.users.entities.User;
import com.departementemployee.users.entities.VerificationToken;
import com.departementemployee.users.exception.EmailAlreadyExistsException;
import com.departementemployee.users.register.RegistationRequest;
import com.departementemployee.users.repos.RoleRepository;
import com.departementemployee.users.repos.UserRepository;
import com.departementemployee.users.repos.VerificationTokenRepository;
import com.departementemployee.users.util.EmailSender;
import java.util.Calendar;
import com.departementemployee.users.exception.ExpiredTokenException;
import com.departementemployee.users.exception.InvalidTokenException;
@Transactional
@Service
public class UserServiceImpl implements UserService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired UserRepository userRep;
    @Autowired RoleRepository roleRep;
    @Autowired BCryptPasswordEncoder bCryptPasswordEncoder;
    @Autowired VerificationTokenRepository verificationTokenRepo;   // nouveau
    @Autowired EmailSender emailSender;                             // nouveau

    @Override
    public User saveUser(User user) {
        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        return userRep.save(user);
    }

    @Override
    public User addRoleToUser(String username, String rolename) {
        User usr = userRep.findByUsername(username);
        Role r = roleRep.findByRole(rolename);
        usr.getRoles().add(r);
        return usr;
    }

    @Override
    public Role addRole(Role role) {
        return roleRep.save(role);
    }

    @Override
    public User findUserByUsername(String username) {
        return userRep.findByUsername(username);
    }

    @Override
    public List<User> findAllUsers() {
        return userRep.findAll();
    }

    @Override
    public User registerUser(RegistationRequest request) {
        Optional<User> optionaluser = userRep.findByEmail(request.getEmail());
        if (optionaluser.isPresent())
            throw new EmailAlreadyExistsException("email déjà existant!");

        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setEmail(request.getEmail());
        newUser.setPassword(bCryptPasswordEncoder.encode(request.getPassword()));
        newUser.setEnabled(false);   // le compte sera activé après la validation de l'email
        userRep.save(newUser);

        // rôle par défaut : USER
        Role r = roleRep.findByRole("USER");
        List<Role> roles = new ArrayList<>();
        roles.add(r);
        newUser.setRoles(roles);
        userRep.save(newUser);

        // génère le code secret et le sauvegarde (valable 15 minutes)
        String code = this.generateCode();
        VerificationToken token = new VerificationToken(code, newUser);
        verificationTokenRepo.save(token);

        // envoie le code par email
        sendEmailUser(newUser, token.getToken());

        return newUser;
    }

    // code à 6 chiffres (entre 100000 et 999999)
    public String generateCode() {
        Random random = new Random();
        Integer code = 100000 + random.nextInt(900000);
        return code.toString();
    }

    // construit le message HTML et l'envoie à l'email de l'utilisateur
    public void sendEmailUser(User u, String code) {
        String emailBody = "Bonjour <h1>" + u.getUsername() + "</h1>"
                + " Votre code de validation est <h1>" + code + "</h1>";
        try {
            emailSender.sendEmail(u.getEmail(), emailBody);
        } catch (RuntimeException e) {
            // Email impossible (Gmail non configuré ou injoignable) : l'inscription n'est pas bloquée.
            // Le code est écrit dans les logs du conteneur (usage local / démonstration) :
            //   docker compose logs users-microservice | Select-String "Code de validation"
            log.warn("Email non envoye a l'utilisateur '{}' ({}). Code de validation : {}",
                    u.getUsername(), e.getMessage(), code);
        }
    }
    @Override
    public User validateToken(String code) {
        VerificationToken token = verificationTokenRepo.findByToken(code);
        if (token == null) {
            throw new InvalidTokenException("Invalid Token");
        }

        User user = token.getUser();
        Calendar calendar = Calendar.getInstance();

        // code expiré : on le supprime et on refuse
        if ((token.getExpirationTime().getTime() - calendar.getTime().getTime()) <= 0) {
            verificationTokenRepo.delete(token);
            throw new ExpiredTokenException("expired Token");
        }

        user.setEnabled(true);   // le compte est activé
        userRep.save(user);
        return user;
    }
}