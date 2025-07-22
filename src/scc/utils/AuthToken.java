package scc.utils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static java.time.Instant.now;

public class AuthToken {
    private static final long EXPIRATION_TIME = 1000L * 60 * 60 * 24; //1 day
    private String username;
    private String tokenID;
    private String jwtToken;


    public AuthToken(String username) {
        this.username = username;
        this.tokenID = UUID.randomUUID().toString();
        String secret = "asdfSFS34wfsdfsdfSDSD32dfsddDDerQSNCK34SOWEK5354fdgdf407";


        Key hmacKey = new SecretKeySpec(Base64.getDecoder().decode(secret),
                SignatureAlgorithm.HS256.getJcaName());

        this.jwtToken = Jwts.builder()
                .claim("username", username)
                .setSubject("Token")
                .setId(tokenID)
                .setIssuedAt(Date.from(now()))
                .setExpiration(Date.from(now().plusMillis(EXPIRATION_TIME)))
                .signWith(hmacKey)
                .compact();


    }


    public long getExpirationTime() {
        return EXPIRATION_TIME;
    }

    public String getTokenID() {
        return tokenID;
    }

    public String getJwtToken() {
        return jwtToken;
    }
}