package it.univaq.f4i.iw.framework.security;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class SecurityHelpers {

    //--------- SESSION SECURITY ------------    
    //questa funzione esegue una serie di controlli di sicurezza
    //sulla sessione corrente. Se la sessione non è valida, la cancella
    //e ritorna null, altrimenti la aggiorna e la restituisce
    //this method executed a set of standard chacks on the current session.
    //If the session exists and is valid, it is returned, otherwise
    //the session is invalidated and the method returns null
    public static HttpSession checkSession(HttpServletRequest r) {
        boolean check = true;

        HttpSession s = r.getSession(false);
        //per prima cosa vediamo se la sessione è attiva
        //first, let's see is the sessione is active
        if (s == null) {
            return null;
        }

        //data/ora correnti
        //current timestamp
        Calendar now = Calendar.getInstance();
        //inizio sessione
        //session start timestamp
        Calendar begin = (Calendar) s.getAttribute("inizio-sessione");
        //ultima azione
        //last action timestamp
        Calendar last = (Calendar) s.getAttribute("ultima-azione");
        //ultima rigenerazione dell'ID
        //last session ID regeneration timestamp
        Calendar refresh = (Calendar) s.getAttribute("ultima-rigenerazione");

        //check sulla validità della sessione
        //second, check is the session contains valid data
        //nota: oltre a controllare se la sessione contiene un userid, 
        //dovremmo anche controllere che lo userid sia valido, probabilmente 
        //consultando il database utenti
        //note: besides checking if the session contains an userid, we should 
        //check if the userid is valid, possibly querying the user database
        if (s.getAttribute("userid") == null) {
            check = false;
            //check sull'ip del client
            //check if the client ip chaged
        } else if ((s.getAttribute("ip") == null) || !((String) s.getAttribute("ip")).equals(r.getRemoteHost())) {
            check = false;
            //check sulle date
            //check if the session is timed out
        } else {
            if (begin == null) {
                check = false;
            } else {
                //secondi trascorsi dall'inizio della sessione
                //seconds from the session start
                long secondsfrombegin = (now.getTimeInMillis() - begin.getTimeInMillis()) / 1000;
                //dopo tre ore la sessione scade
                //after three hours the session is invalidated
                if (secondsfrombegin > 3 * 60 * 60) {
                    check = false;
                } else if (last != null) {
                    //secondi trascorsi dall'ultima azione
                    //seconds from the last valid action
                    long secondsfromlast = (now.getTimeInMillis() - last.getTimeInMillis()) / 1000;
                    //dopo trenta minuti dall'ultima operazione la sessione è invalidata
                    //after 30 minutes since the last action the session is invalidated                    
                    if (secondsfromlast > 30 * 60) {
                        check = false;
                    }
                }
            }
        }
        if (!check) {
            s.invalidate();
            return null;
        } else {
            //ogni 120 secondi, rigeneriamo la sessione per cambiarne l'ID
            //every 120 seconds, we regenerate the session to change its ID
            if (refresh == null) {
                refresh = begin;
            }
            long secondsfromlastregen = (now.getTimeInMillis() - refresh.getTimeInMillis()) / 1000;
            if (secondsfromlastregen >= 120) {
                s = regenerateSession(r);
                s.setAttribute("ultima-rigenerazione", now);
            }

            //reimpostiamo la data/ora dell'ultima azione
            //if che checks are ok, update the last action timestamp
            s.setAttribute("ultima-azione", now);
            return s;
        }
    }

    public static HttpSession createSession(HttpServletRequest request, String username, int userid) {
        //se una sessione è già attiva, rimuoviamola e creiamone una nuova
        //if a session already exists, remove it and recreate a new one
        disposeSession(request);
        HttpSession s = request.getSession(true);
        s.setAttribute("username", username);
        s.setAttribute("ip", request.getRemoteHost());
        s.setAttribute("inizio-sessione", Calendar.getInstance());
        s.setAttribute("userid", userid);
        return s;
    }

    public static void disposeSession(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s != null) {
            s.invalidate();
        }
    }

    //questo metodo rigenera la sessione invalidando quella corrente e
    //creandone una nuova con gli stessi attributi. Può essere utile per 
    //prevenire il session hijacking, perchè modifica il session identidier
    //this method regenerates the session by invalidating the current one
    //and creating a new one with the same attributes. It may be useful
    //to prevent session hijacking, since it changes the session identifier
    public static HttpSession regenerateSession(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        Enumeration<String> attributeNames = s.getAttributeNames();
        Map<String, Object> attributes = new HashMap<>();
        while (attributeNames.hasMoreElements()) {
            String key = attributeNames.nextElement();
            Object value = s.getAttribute(key);
            attributes.put(key, value);
        }
        s.invalidate();
        s = request.getSession(true);
        for (String key : attributes.keySet()) {
            Object value = attributes.get(key);
            s.setAttribute(key, value);
        }
        return s;
    }

    //--------- CONNECTION SECURITY ------------
    //questa funzione verifica se il protocollo HTTPS è attivo
    //checks if the HTTPS protocol is in use
    public static boolean checkHttps(HttpServletRequest r) {
        return r.isSecure();
        //metodo "fatto a mano" che funziona solo se il server trasmette gli header corretti
        //the following is an "handmade" alternative, which works only if the server sends correct headers
        //String httpsheader = r.getHeader("HTTPS");
        //return (httpsheader != null && httpsheader.toLowerCase().equals("on"));
    }

    //questa funzione ridirige il browser sullo stesso indirizzo
    //attuale, ma con protocollo https
    //this function redirects the browser on the current address, but
    //with https protocol
    public static void redirectToHttps(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        //estraiamo le parti della request url
        String server = request.getServerName();
        //int port = request.getServerPort();
        String context = request.getContextPath();
        String path = request.getServletPath();
        String info = request.getPathInfo();
        String query = request.getQueryString();

        //ricostruiamo la url cambiando il protocollo e la porta COME SPECIFICATO NELLA CONFIGURAZIONE DI TOMCAT
        //rebuild the url changing port and protocol AS SPECIFIED IN THE SERVER CONFIGURATION
        String newUrl = "https://" + server + ":8443" + context + path + (info != null ? info : "") + (query != null ? "?" + query : "");
        try {
            //ridirigiamo il client
            //redirect
            response.sendRedirect(newUrl);
        } catch (IOException ex) {
            try {
                //in caso di problemi tentiamo prima di inviare un errore HTTP standard
                //in case of problems, first try to send a standard HTTP error message
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot redirect to HTTPS, blocking request");
            } catch (IOException ex1) {
                //altrimenti generiamo un'eccezione
                //otherwise, raise an exception
                throw new ServletException("Cannot redirect to https!");
            }
        }
    }

    //--------- DATA SECURITY ------------
    //questa funzione aggiunge un backslash davanti a
    //tutti i caratteri "pericolosi", usati per eseguire
    //SQL injection attraverso i parametri delle form
    //this function adds backslashes in front of
    //all the "malicious" charcaters, usually exploited
    //to perform SQL injection through form parameters
    public static String addSlashes(String s) {
        return s.replaceAll("(['\"\\\\])", "\\\\$1");
    }

    //questa funzione rimuove gli slash aggiunti da addSlashes
    //this function removes the slashes added by addSlashes
    public static String stripSlashes(String s) {
        return s.replaceAll("\\\\(['\"\\\\])", "$1");
    }

    public static int checkNumeric(String s) throws NumberFormatException {
        //convertiamo la stringa in numero, ma assicuriamoci prima che sia valida
        //convert the string to a number, ensuring its validity
        if (s != null) {
            //se la conversione fallisce, viene generata un'eccezione
            //if the conversion fails, an exception is raised
            return Integer.parseInt(s);
        } else {
            throw new NumberFormatException("String argument is null");
        }
    }

    public static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    //--------- PASSWORD SECURITY ------------
    //support functions for the password hashing functions
    private static String bytesToHexString(byte[] byteArray) {
        StringBuilder hexStringBuffer = new StringBuilder();
        for (int i = 0; i < byteArray.length; i++) {
            char[] hexDigits = new char[2];
            hexDigits[0] = Character.forDigit((byteArray[i] >> 4) & 0xF, 16);
            hexDigits[1] = Character.forDigit((byteArray[i] & 0xF), 16);
            hexStringBuffer.append(new String(hexDigits));
        }
        return hexStringBuffer.toString();
    }

    private static byte[] hexStringToBytes(String hexString) {
        byte[] byteArray = new byte[hexString.length() / 2];
        for (int i = 0; i < byteArray.length; i++) {
            int val = Integer.parseInt(hexString.substring(i * 2, i * 2 + 2), 16);
            byteArray[i] = (byte) val;
        }
        return byteArray;
    }

    //password hashing with SHA-512 + salt
    private static String getPasswordHashSHA(String password, byte[] salt) throws NoSuchAlgorithmException {
        if (salt.length != 16) {
            throw new IllegalArgumentException("Salt must be 16 bytes");
        }
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(salt);
        md.update(password.getBytes());
        byte[] digest = md.digest();
        return bytesToHexString(salt) + bytesToHexString(digest);
    }

    public static String getPasswordHashSHA(String password) throws NoSuchAlgorithmException {
        byte[] salt = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        return getPasswordHashSHA(password, salt);
    }

    //check password hashed by getPasswordHashSHA
    public static boolean checkPasswordHashSHA(String password, String passwordhash) throws NoSuchAlgorithmException {
        byte[] salt = new byte[16];
        System.arraycopy(hexStringToBytes(passwordhash), 0, salt, 0, 16);
        return getPasswordHashSHA(password, salt).equals(passwordhash);
    }

    //password hashing with PBKDF2 + salt
    private static String getPasswordHashPBKDF2(String password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (salt.length != 16) {
            throw new IllegalArgumentException("Salt must be 16 bytes");
        }
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        byte[] digest = factory.generateSecret(spec).getEncoded();
        return bytesToHexString(salt) + bytesToHexString(digest);
    }

    public static String getPasswordHashPBKDF2(String password) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] salt = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        return getPasswordHashPBKDF2(password, salt);
    }

    //check password hashed by getPasswordHashPBKDF2
    public static boolean checkPasswordHashPBKDF2(String password, String passwordhash) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] salt = new byte[16];
        System.arraycopy(hexStringToBytes(passwordhash), 0, salt, 0, 16);
        return (getPasswordHashPBKDF2(password, salt)).equals(passwordhash);
    }

}
