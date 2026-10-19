package Ex1;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServeurPOP {
    private static final Map<String, String> DESTINATAIRES = new HashMap<>();
    private static final int SOCKET = 8080;

    static {
        DESTINATAIRES.put("asterix", "123");
        DESTINATAIRES.put("obelix", "12");
        DESTINATAIRES.put("cesar", "123");
    }

    private static final String COMMANDES =
            "STAT : obtenir le nombre de mails en attente\n"
            + "LIST (<n>) : obtenir les infos sur le message n\n"
            + "RETR <n> : recuperer le mail selectionne\n"
            + "TOP <n> <x> : recuperer les x premieres lignes du message n\n"
            + "DELE <n> : supprimer le mail selectionne\n"
            + "RSET : annuler toutes les commandes de suppression de la session\n"
            + "NOOP : ne rien faire\n"
            + "QUIT : quitter la session\n";

    private static final String MESSAGE_CONNEXI0N =
            "Connectez-vous en utilisant les commandes suivantes :\n"
            + "USER <user> : pour choisir votre utilisateur\n"
            + "PASS <mdp> : pour rentrer votre mot de passe\n";

    public static void main(String[] args) {
        initialiserBoites();

        int port = SOCKET;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        try {
            ServerSocket socketServeur = new ServerSocket(port);
            System.out.println("Serveur POP3 demarre sur le port " + port);
            while (true) {
                Socket socketClient = socketServeur.accept();
                System.out.println("Connexion : " + socketClient.getInetAddress());

                Thread thread = new Thread(() -> gererClient(socketClient));
                thread.start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void initialiserBoites() {
        File dossierBoites = new File("boites");
        if (!dossierBoites.exists()) dossierBoites.mkdirs();

        for (String u : DESTINATAIRES.keySet()) {
            File b = new File(dossierBoites, u);
            if (!b.exists()) b.mkdirs();
        }

        File mail1 = new File("boites/asterix/1.txt");
        if (!mail1.exists()) {
            creerMail(mail1,
                    "From: cesar@rome.it\r\n" +
                    "To: asterix@village.ga\r\n" +
                    "Subject: Treve gauloise\r\n" +
                    "\r\n" +
                    "Bonjour Asterix,\r\n" +
                    "Je propose une treve pour aujourd'hui.\r\n" +
                    "Ave Cesar !\r\n");
        }

        File mail2 = new File("boites/asterix/2.txt");
        if (!mail2.exists()) {
            creerMail(mail2,
                    "From: panoramix@village.ga\r\n" +
                    "To: asterix@village.ga\r\n" +
                    "Subject: Potion magique prete\r\n" +
                    "\r\n" +
                    "Salut Asterix,\r\n" +
                    "La marmite de potion magique est prete.\r\n" +
                    "Panoramix.\r\n");
        }
    }

    private static void creerMail(File f, String contenu) {
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            osw.write(contenu);
        } catch (IOException ignored) {}
    }

    private static void gererClient(Socket socketClient) {
        try {
            BufferedReader entree = new BufferedReader(new InputStreamReader(socketClient.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter sortie = new PrintWriter(new OutputStreamWriter(socketClient.getOutputStream(), StandardCharsets.UTF_8), true);
            sortie.println("+OK serveur POP pret");

            String user = null;
            Boolean auth = false;
            File[] mails = null;
            boolean[] supprime = null;

            String ligne;

            while ((ligne = entree.readLine()) != null) {
                ligne = ligne.trim();
                if (ligne.isEmpty()) continue;

                String[] mots = ligne.split("\\s+");
                String commande = mots[0].toUpperCase();

                if (!auth) {
                    if (commande.equals("USER") && mots.length >= 2) {
                        user = mots[1].toLowerCase();
                        if (DESTINATAIRES.containsKey(user)) {
                            sortie.println("+OK entrez maintenant votre mot de passe : PASS <mdp>");
                        } else {
                            user = null;
                            sortie.println("-ERR utilisateur inconnu");
                        }
                    } else if (commande.equals("PASS") && mots.length >= 2) {
                        if (user != null && mots[1].equals(DESTINATAIRES.get(user))) {
                            auth = true;

                            File dossierUser = new File("boites/" + user);
                            if (!dossierUser.exists()) dossierUser = new File("boites", user);
                            if (!dossierUser.exists()) dossierUser = new File("src/Mails/" + Character.toUpperCase(user.charAt(0)) + user.substring(1));
                            if (!dossierUser.exists()) dossierUser.mkdirs();

                            File[] fichiers = dossierUser.listFiles(File::isFile);
                            if (fichiers != null) {
                                Arrays.sort(fichiers, Comparator.comparing(File::getName));
                                mails = fichiers;
                            } else {
                                mails = new File[0];
                            }
                            supprime = new boolean[mails.length];

                            sortie.println("+OK connecte en tant que " + user);
                        } else {
                            user = null;
                            sortie.println("-ERR utilisateur ou mot de passe incorrect");
                        }
                    } else if (commande.equals("QUIT")) {
                        sortie.println("+OK au revoir");
                        break;
                    } else {
                        sortie.println("-ERR " + MESSAGE_CONNEXI0N);
                    }
                } else {
                    if (commande.equals("STAT")) {
                        int nb = 0;
                        long taille = 0;
                        for (int i = 0; i < mails.length; i++) {
                            if (!supprime[i]) {
                                nb++;
                                taille += mails[i].length();
                            }
                        }
                        sortie.println("+OK " + nb + " " + taille);
                    } else if (commande.equals("LIST") && mots.length == 1) {
                        int nb = 0;
                        long taille = 0;
                        for (int i = 0; i < mails.length; i++) {
                            if (!supprime[i]) {
                                nb++;
                                taille += mails[i].length();
                            }
                        }
                        sortie.println("+OK " + nb + " messages (" + taille + " octets)");
                        for (int i = 0; i < mails.length; i++) {
                            if (!supprime[i]) sortie.println((i + 1) + " " + mails[i].length());
                        }
                        sortie.println(".");
                    } else if (commande.equals("RSET")) {
                        Arrays.fill(supprime, false);
                        sortie.println("+OK suppressions annulees");
                    } else if (commande.equals("NOOP")) {
                        sortie.println("+OK");
                    } else if (commande.equals("QUIT")) {
                        for (int i = 0; i < mails.length; i++) {
                            if (supprime[i] && mails[i].exists()) {
                                mails[i].delete();
                            }
                        }
                        sortie.println("+OK au revoir");
                        break;
                    } else {
                        if (mots.length < 2) {
                            sortie.println("-ERR argument manquant");
                            continue;
                        }

                        int inputNum;
                        try {
                            inputNum = Integer.parseInt(mots[1]);
                        } catch (NumberFormatException e) {
                            sortie.println("-ERR numero de message invalide");
                            continue;
                        }

                        // Accepte a la fois l'indexation 0 (0 = 1er mail) et 1 (1 = 1er mail)
                        int n;
                        if (inputNum == 0 && mails.length > 0) {
                            n = 0;
                        } else if (inputNum >= 1 && inputNum <= mails.length) {
                            n = inputNum - 1;
                        } else {
                            sortie.println("-ERR numero de message invalide");
                            continue;
                        }

                        if (supprime[n]) {
                            sortie.println("-ERR message " + (n + 1) + " supprime");
                        } else if (commande.equals("LIST")) {
                            sortie.println("+OK " + (n + 1) + " " + mails[n].length());
                        } else if (commande.equals("RETR")) {
                            try {
                                envoyer(sortie, mails[n], Integer.MAX_VALUE);
                            } catch (Exception e) {
                                sortie.println("-ERR erreur lecture mail : " + e.getMessage());
                            }
                        } else if (commande.equals("TOP")) {
                            if (mots.length < 3) {
                                sortie.println("-ERR argument x manquant pour TOP");
                            } else {
                                try {
                                    int x = Integer.parseInt(mots[2]);
                                    envoyer(sortie, mails[n], x);
                                } catch (NumberFormatException e) {
                                    sortie.println("-ERR nombre de lignes invalide");
                                } catch (Exception e) {
                                    sortie.println("-ERR erreur lecture mail : " + e.getMessage());
                                }
                            }
                        } else if (commande.equals("DELE")) {
                            supprime[n] = true;
                            sortie.println("+OK message " + (n + 1) + " marque pour suppression");
                        } else {
                            sortie.println("-ERR commande inconnue");
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Client deconnecte : " + e.getMessage());
        } finally {
            try {
                socketClient.close();
            } catch (IOException ignored) {}
        }
    }

    private static void envoyer(PrintWriter sortie, File mail, int nbLignes) throws IOException {
        List<String> lignes = Files.readAllLines(mail.toPath(), StandardCharsets.UTF_8);
        sortie.println("+OK");
        boolean corps = false;
        for (String l : lignes) {
            if (corps && nbLignes-- <= 0) break;
            if (l.isEmpty()) corps = true;
            sortie.println(l.startsWith(".") ? "." + l : l);
        }
        sortie.println(".");
    }
}
