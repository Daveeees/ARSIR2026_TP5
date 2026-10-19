package Ex1;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.Buffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServeurPOP {
    private static final Map<String, String> DESTINATAIRES = new HashMap<>();
    private static final int SOCKET = 8080;
        static {
            DESTINATAIRES.put("asterix", "123");
            DESTINATAIRES.put("obelix", "12");
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
            File[] mails = null;        // boîte de l'utilisateur (null tant qu'il n'est pas authentifié)
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
                        if (user != null && mots[1].equals(DESTINATAIRES.get(user))) {
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

                    if (commande.equals("STAT")){
                        int nb = 0;
                        long taille = 0;
                        for (int i = 0; i < mails.length; i++)
                            if (!supprime[i]) { nb++; taille += mails[i].length(); }
                        sortie.println("+OK " + nb + " " + taille);
                    }
                    else if (commande.equals("LIST") && mots.length == 1){
                        sortie.println("+OK");
                        for (int i = 0; i < mails.length; i++)
                            if (!supprime[i]) sortie.println((i + 1) + " " + mails[i].length());
                        sortie.println(".");
                    }
                    else if (commande.equals("RSET")){
                        Arrays.fill(supprime, false);
                        sortie.println("+OK suppressions annulees");
                    }
                    else if (commande.equals("NOOP")){
                        sortie.println("+OK");
                    }
                    else {
                        // commandes avec un numéro de message : LIST n, RETR n, TOP n x, DELE n

                        // le message 1 est la case 0
                        int n = Integer.parseInt(mots[1]) - 1;
                        if (supprime[n]) sortie.println("-ERR message " + (n + 1) + " supprime");
                        else if (commande.equals("LIST")) sortie.println("+OK " + (n + 1) + " " + mails[n].length());
                        else if (commande.equals("RETR")) envoyer(sortie, mails[n], Integer.MAX_VALUE);
                        else if (commande.equals("TOP")) envoyer(sortie, mails[n], Integer.parseInt(mots[2]));
                        else if (commande.equals("DELE")) {
                            supprime[n] = true;
                            sortie.println("+OK message " + (n + 1) + " marque pour suppression");
                        }
                        else sortie.println("-ERR commande inconnue");
                    }

                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void envoyer(PrintWriter sortie, File mail, int nbLignes) throws IOException {
        List<String> lignes = Files.readAllLines(mail.toPath());
        sortie.println("+OK");
        boolean corps = false;
        for (String l : lignes) {
            if (corps && nbLignes-- <= 0) break;
            // ligne vide = fin des en-têtes
            if (l.isEmpty()) corps = true;
            sortie.println(l.startsWith(".") ? "." + l : l);
        }
        sortie.println(".");
    }
}
