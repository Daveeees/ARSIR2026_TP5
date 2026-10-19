package Ex1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.Buffer;
import java.util.HashMap;
import java.util.Map;

public class ServeurPOP {
    private static final Map<String, String> DESTINATAIRES = new HashMap<>();
    private static final int SOCKET = 8080;
        static {
            DESTINATAIRES.put("123", "123");
            DESTINATAIRES.put("12", "12");
        }
    private static final String COMMANDES =
            "STAT : avoir obtenir le nombre de mails en attente \n"
            + "LIST <n>: obtenir les infos sur le message n \n"
            + "RETR <n> : récupérer le mail séléctionné \n"
            + "TOP <n> <x> : récupérer les x premières lignes du message n \n"
            + "DELE <n> : supprimer le mail sélectionné \n"
            + "RSET : annuler toutes les commandes de suppression de la session \n"
            + "NOOP : ne rien faire \n"
            + "QUIT : quitter la session \n";
    private static final String MESSAGE_CONNEXI0N =
            "Connectez vous en utilisant les commandes suivants : \n"
            + "USER <user> : pour choisir votre utilisateur \n"
            + "PASS <mdp> : pour rentrer votre mot de passe \n";
    public static void main(String[] args){

        try{
            ServerSocket socketServeur = new ServerSocket(SOCKET);
            while(true){
                Socket socketClient = socketServeur.accept();
                System.out.println("Connexion : " + socketClient.getInetAddress());

                Thread thread = new Thread(() -> gererClient(socketClient));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void gererClient(Socket socketClient) {
        try{
            BufferedReader entree = new BufferedReader(new InputStreamReader(socketClient.getInputStream()));
            PrintWriter sortie = new PrintWriter(socketClient.getOutputStream(), true);
            sortie.println("+OK serveur POP pret");

            String user = null;
            Boolean auth = false;
            String[] mails = null;        // boîte de l'utilisateur (null tant qu'il n'est pas authentifié)
            boolean[] supprime = null;  // mails marqués par DELE

            String ligne;

            while ((ligne = entree.readLine()) != null){
                String[] mots = ligne.split(" ");
                String commande =  mots[0];
                if (!auth) {
                    if (commande.equals("USER") && mots.length == 2) {
                        // on retient juste le nom, la vérification se fait au PASS
                        user = mots[1];
                        sortie.println("+OK entrez maintenant votre mot de passe : PASS <mdp>");
                    } else if (commande.equals("PASS") && mots.length == 2) {
                        // vérification du couple user / mdp dans le dictionnaire
                        if (user != null && DESTINATAIRES.containsKey(user) && mots[1].equals(DESTINATAIRES.get(user))) {
                            auth = true;
                            sortie.println("+OK connecte en tant que " + user);
                            sortie.println("Vous pouvez utiliser les commandes suivantes : \n" + COMMANDES);
                        } else {
                            // on recommence depuis USER
                            user = null;
                            sortie.println("-ERR utilisateur ou mot de passe incorrect");
                        }
                    } else {
                        sortie.println("-ERR " + MESSAGE_CONNEXI0N);
                    }
                }
                else{
                    sortie.println("Vous pouvez utiliser les commandes suivantes : \n" + COMMANDES);

                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
