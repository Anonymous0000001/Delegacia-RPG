package com.seuusuario.delegaciarpg;

import java.util.Random;

public class NPCInteractionManager {
    private final Random random = new Random();

    private final String[] falasDetetives = {
            "Chefe, a boa notícia é que achei o esconderijo do gangue. A má notícia é que eles têm mais donuts que nós.",
            "Eu juro que tentei investigar o caso, mas a máquina de café engoliu minhas notas de R$ 100.",
            "Capitão, interroguei o suspeito. Ele confessou... que assistiu à novela inteira e não lembra de nada.",
            "Se a gente pintar as paredes da delegacia de verde, o mofo finge que é decoração moderna!"
    };

    private final String[] falasPresos = {
            "Ei, Capitão! Quando é que vai rolar o serviço de quarto? A pia daqui tá vazando.",
            "Eu sou inocente! Aquele banco assaltou a mim moralmente!",
            "Se o senhor me soltar, eu prometo capinar o mato na frente da delegacia de graça.",
            "A comida da prisão tá tão ruim que até o rato daqui fugiu para a delegacia vizinha."
    };

    private final String[] falasCapitao = {
            "Eu só queria uma aposentadoria tranquila na praia...",
            "Quem foi que colou a porta do banheiro com fita isolante?",
            "Minha espingarda Calibre 12 tá mais limpa que a moral dessa cidade.",
            "Se chover mais um pouco, o teto da recepção cai na minha cabeça."
    };

    public String getFalaDetetive() {
        return falasDetetives[random.nextInt(falasDetetives.length)];
    }

    public String getFalaPreso() {
        return falasPresos[random.nextInt(falasPresos.length)];
    }

    public String getFalaCapitao() {
        return falasCapitao[random.nextInt(falasCapitao.length)];
    }
}