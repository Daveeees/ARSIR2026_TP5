package Ex3;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ClientIMAP {

    private static final String SERVEUR_DEFAUT = "localhost";
    private static final int PORT_DEFAUT = 143;
    private static final String UTILISATEUR_DEFAUT = "asterix";
    private static final String MOT_DE_PASSE_DEFAUT = "123";

    private static BufferedReader entree;
    private static PrintWriter sortie;
    private static int compteurTag = 1;

    public static void main(String[] args) {
        String serveur = args.length > 0 ? args[0] : SERVEUR_DEFAUT;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : PORT_DEFAUT;
        String utilisateur = args.length > 2 ? args[2] : UTILISATEUR_DEFAUT;
        String motDePasse = args.length > 3 ? args[3] : MOT_DE_PASSE_DEFAUT;

        System.out.println("==================================================");
        System.out.println("         CLIENT IMAP - Polytech Lyon (4A)        ");
        System.out.println("==================================================");
        System.out.println("Connexion au serveur IMAP " + serveur + ":" + port + "...");

        Socket socket = null;
        try {
            // Tentative de connexion avec repli automatique (utile en environnement WSL/Windows)
            socket = connecterSocket(serveur, port);
            entree = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            sortie = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            // Lecture de la bannière initiale du serveur (* OK ...)
            String banniere = entree.readLine();
            System.out.println("[Serveur] " + banniere);

            // 1. Authentification
            System.out.println("\n[1] Authentification de l'utilisateur : " + utilisateur);
            ReponseIMAP repLogin = executerCommande("LOGIN " + utilisateur + " " + motDePasse);
            if (!repLogin.estReussie()) {
                System.err.println("Échec de l'authentification : " + repLogin.ligneStatut);
                fermerSession(socket);
                return;
            }
            System.out.println(" Authentification réussie !");

            // 2. Sélection de la boîte de réception (INBOX)
            System.out.println("\n[2] Sélection de la boîte INBOX");
            ReponseIMAP repSelect = executerCommande("SELECT INBOX");
            if (!repSelect.estReussie()) {
                System.err.println("Impossible d'ouvrir INBOX : " + repSelect.ligneStatut);
                fermerSession(socket);
                return;
            }

            // 3. Recherche des mails non lus (SEARCH UNSEEN)
            System.out.println("\n[3] Recherche des mails non lus (SEARCH UNSEEN)...");
            ReponseIMAP repSearch = executerCommande("SEARCH UNSEEN");
            List<Integer> mailsNonLus = extraireIdsMails(repSearch);

            if (mailsNonLus.isEmpty()) {
                System.out.println(" Aucun mail non lu trouvé dans la boîte INBOX.");
            } else {
                System.out.println("-> " + mailsNonLus.size() + " mail(s) non lu(s) détecté(s) : " + mailsNonLus);

                // 4. Affichage du contenu de chaque mail non lu
                for (int idMail : mailsNonLus) {
                    System.out.println("\n--------------------------------------------------");
                    System.out.println("           CONTENU DU MAIL NON LU N°" + idMail);
                    System.out.println("--------------------------------------------------");
                    recupererEtAfficherContenuMail(idMail);

                    // 5. Marquage du message pour suppression (\Deleted)
                    System.out.println("\n[Marquage] Ajout du drapeau \\Deleted sur le mail n°" + idMail + "...");
                    ReponseIMAP repStore = executerCommande("STORE " + idMail + " +FLAGS (\\Deleted)");
                    if (repStore.estReussie()) {
                        System.out.println(" Mail n°" + idMail + " marqué avec succès comme \\Deleted.");
                    } else {
                        System.err.println(" Erreur lors du marquage du mail : " + repStore.ligneStatut);
                    }
                }

                // 6. Suppression définitive (EXPUNGE puis CLOSE)
                System.out.println("\n[4] Confirmation de la suppression définitive (EXPUNGE)...");
                ReponseIMAP repExpunge = executerCommande("EXPUNGE");
                for (String l : repExpunge.lignesNonTaguees) {
                    System.out.println("    " + l);
                }
                System.out.println(" Messages purgés définitivement du serveur.");
            }

            // Fermeture propre du dossier
            executerCommande("CLOSE");

            // 7. Déconnexion
            System.out.println("\n[5] Déconnexion (LOGOUT)...");
            executerCommande("LOGOUT");
            System.out.println("Session IMAP terminée avec succès.");

        } catch (Exception e) {
            System.err.println("Erreur client IMAP : " + e.getMessage());
            e.printStackTrace();
        } finally {
            fermerSession(socket);
        }
    }

    /**
     * Tente la connexion à l'hôte indiqué. Si la connexion échoue sur localhost
     * et qu'une IP WSL alternative est détectée, tente la connexion de secours.
     */
    private static Socket connecterSocket(String host, int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return socket;
        } catch (IOException e) {
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
                // Essai sur l'interface WSL courante si applicable
                String[] secoursHosts = {"172.24.34.55", "127.0.0.1"};
                for (String altHost : secoursHosts) {
                    try {
                        Socket altSocket = new Socket();
                        altSocket.connect(new InetSocketAddress(altHost, port), 2000);
                        System.out.println("[Info] Connexion établie via l'hôte alternatif : " + altHost);
                        return altSocket;
                    } catch (IOException ignored) {}
                }
            }
            throw e;
        }
    }

    /**
     * Envoie une commande IMAP avec un tag unique et attend la réponse étiquetée correspondante.
     */
    private static ReponseIMAP executerCommande(String commande) throws IOException {
        String tag = String.format("A%03d", compteurTag++);
        String ligneCommande = tag + " " + commande;

        sortie.print(ligneCommande + "\r\n");
        sortie.flush();
        System.out.println("> " + ligneCommande);

        ReponseIMAP reponse = new ReponseIMAP(tag);
        String ligne;
        while ((ligne = entree.readLine()) != null) {
            if (ligne.startsWith(tag + " ")) {
                reponse.ligneStatut = ligne;
                reponse.succes = ligne.startsWith(tag + " OK");
                break;
            } else {
                reponse.lignesNonTaguees.add(ligne);
            }
        }
        return reponse;
    }

    /**
     * Récupère l'intégralité du corps d'un message donné via FETCH <id> BODY[]
     * et l'affiche sur la sortie standard.
     */
    private static void recupererEtAfficherContenuMail(int idMail) throws IOException {
        String tag = String.format("A%03d", compteurTag++);
        String ligneCommande = tag + " FETCH " + idMail + " BODY[]";

        sortie.print(ligneCommande + "\r\n");
        sortie.flush();
        System.out.println("> " + ligneCommande);

        String ligne;
        boolean dansCorps = false;
        while ((ligne = entree.readLine()) != null) {
            if (ligne.startsWith(tag + " ")) {
                break; // Fin de la commande FETCH
            }
            // Début du littéral de corps : * <id> FETCH (FLAGS (...) BODY[] {taille}
            if (ligne.contains("BODY[]") && ligne.endsWith("{") || ligne.matches(".*\\{\\d+\\}$")) {
                dansCorps = true;
                continue;
            }
            // Fin de la réponse FETCH fermée par ')'
            if (dansCorps && ligne.equals(")")) {
                dansCorps = false;
                continue;
            }
            System.out.println(ligne);
        }
    }

    /**
     * Extrait les identifiants de messages à partir de la réponse SEARCH UNSEEN.
     * Exemple de réponse non taguée : "* SEARCH 1 3 4"
     */
    private static List<Integer> extraireIdsMails(ReponseIMAP rep) {
        List<Integer> ids = new ArrayList<>();
        for (String ligne : rep.lignesNonTaguees) {
            if (ligne.startsWith("* SEARCH")) {
                String[] parts = ligne.split("\\s+");
                for (int i = 2; i < parts.length; i++) {
                    try {
                        ids.add(Integer.parseInt(parts[i]));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return ids;
    }

    private static void fermerSession(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Structure de données représentant la réponse à une commande IMAP taguée.
     */
    private static class ReponseIMAP {
        final String tag;
        boolean succes = false;
        String ligneStatut = "";
        final List<String> lignesNonTaguees = new ArrayList<>();

        ReponseIMAP(String tag) {
            this.tag = tag;
        }

        boolean estReussie() {
            return succes;
        }
    }
}
