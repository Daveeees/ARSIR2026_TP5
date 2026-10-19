import Ex1.ServeurPOP;
import Ex3.ClientIMAP;

public class Main {
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   TP n°5 : Protocoles POP et IMAP - Polytech   ");
        System.out.println("=================================================");
        System.out.println("Ce projet contient deux exécutables principaux :");
        System.out.println(" 1. Ex1.ServeurPOP : Serveur POP3 gérant 2 utilisateurs (boîtes et fichiers texte)");
        System.out.println(" 2. Ex3.ClientIMAP : Client IMAP téléchargeant et supprimant les mails non lus");
        System.out.println("=================================================");
        System.out.println("Pour lancer le serveur POP3 :");
        System.out.println("   java -cp bin Ex1.ServeurPOP [port]");
        System.out.println("Pour lancer le client IMAP :");
        System.out.println("   java -cp bin Ex3.ClientIMAP [serveur] [port] [utilisateur] [motdepasse]");
        System.out.println("=================================================");
    }
}
