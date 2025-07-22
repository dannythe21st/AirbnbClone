package scc.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;

public class JWTValidation {

    private static String secret = "asdfSFS34wfsdfsdfSDSD32dfsddDDerQSNCK34SOWEK5354fdgdf407";
    public JWTValidation() {}

    public static Jws<Claims> parseJwt(String jwtString) {
        Key hmacKey = new SecretKeySpec(Base64.getDecoder().decode(secret),
                SignatureAlgorithm.HS256.getJcaName());
        try {
            Jws<Claims> jwt = Jwts.parserBuilder()
                    .setSigningKey(hmacKey)
                    .build()
                    .parseClaimsJws(jwtString);
            /*
                Verificar se a token existe na base dados, se já não estiver valida,
                 apaga-se da base de dados e envia-se 409
             */
            return jwt;
        } catch (Exception e) {
            return null;
        }

    }

}
