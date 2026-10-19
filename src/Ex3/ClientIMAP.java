package Ex3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientIMAP {
    private static final String HOTE = "localhost";
    private static final int PORT = 143;
    private static final String UTILISATEUR = "asterix";   // à adapter
    private static final String MOT_DE_PASSE = "potion";   // à adapter

    private static BufferedReader entree;
    private static PrintWriter sortie;
    private static int numero = 0;   // pour numéroter les commandes : A1, A2, A3...

    public static void main(String[] args) throws IOException {
        Socket socket = new Socket(HOTE, PORT);
        entree = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        sortie = new PrintWriter(socket.getOutputStream());

        // connexion
        List<String> reponse = commande("LOGIN " + UTILISATEUR + " " + MOT_DE_PASSE);
        if (!reponse.get(reponse.size() - 1).split(" ")[1].equals("OK")) {
            System.out.println("Utilisateur ou mot de passe incorrect");
            socket.close();
            return;
        }
        commande("SELECT INBOX");

        // numéros des mails non lus : le serveur répond "* SEARCH 2 3"
        String numeros = "";
        for (String ligne : commande("SEARCH UNSEEN"))
            if (ligne.startsWith("* SEARCH")) numeros = ligne.substring(8).trim();

        if (numeros.isEmpty()) {
            System.out.println("Aucun mail non lu");
        }
        else {
            for (String n : numeros.split(" ")) {
                // réponse : "* n FETCH (... BODY[] {taille}", les lignes du mail, ")", "An OK ..."
                List<String> mail = commande("FETCH " + n + " BODY[]");
                System.out.println("===== Mail " + n + " =====");
                for (int i = 1; i < mail.size() - 2; i++)
                    System.out.println(mail.get(i));

                commande("STORE " + n + " +FLAGS (\\Deleted)");   // marqué pour suppression
            }
        }

        commande("CLOSE");    // supprime définitivement les mails marqués \Deleted
        commande("LOGOUT");
        socket.close();
    }

    // Envoie une commande précédée d'un identifiant unique (A1, A2...)
    // et renvoie toutes les lignes reçues jusqu'à la ligne "An OK/NO/BAD ..." qui termine la réponse
    private static List<String> commande(String cmd) throws IOException {
        String id = "A" + (++numero);
        sortie.print(id + " " + cmd + "\r\n");
        sortie.flush();

        List<String> reponse = new ArrayList<>();
        String ligne;
        do {
            ligne = entree.readLine();
            reponse.add(ligne);
        } while (!ligne.startsWith(id + " "));
        return reponse;
    }
}