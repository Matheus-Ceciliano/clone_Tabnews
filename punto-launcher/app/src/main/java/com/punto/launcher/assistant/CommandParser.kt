package com.punto.launcher.assistant

import java.text.Normalizer
import java.util.Locale

/** O que o usuário pediu, já entendido. */
sealed interface Command {
    data object Help : Command
    data object AirConditioning : Command
    data class WhatsApp(val contact: String, val message: String) : Command
    data class Call(val contact: String) : Command
    data class Navigate(val destination: String) : Command
    data object CarData : Command
    data object Weather : Command
    data object Time : Command
    data object Speed : Command
    data object Trip : Command
    data object VolumeUp : Command
    data object VolumeDown : Command
    data object Pause : Command
    data object Next : Command
    data object Previous : Command
    data object Resume : Command

    /** [app]: "spotify", "ytmusic" ou null (usa o padrão). */
    data class PlayMusic(val query: String, val app: String?) : Command
    data class OpenApp(val name: String) : Command
    data class Unknown(val text: String) : Command
}

/** Minúsculas e sem acentos: "Música" -> "musica". */
fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(MARKS, "")

private val MARKS = Regex("\\p{Mn}+")

/**
 * Texto original + versão normalizada com os mesmos índices,
 * para casar regex sem acento e recortar o texto com acento ("São Paulo").
 */
private class Txt(original: String) {
    val n: String = norm(original)
    val src: String = if (n.length == original.length) original else n

    fun slice(range: IntRange): String = src.substring(range.first, range.last + 1)

    fun stripStart(re: Regex): Txt {
        val m = re.find(n) ?: return this
        if (m.range.first != 0 || m.value.isEmpty()) return this
        return Txt(src.substring(m.range.last + 1))
    }

    fun stripEnd(re: Regex): Txt {
        val m = re.find(n) ?: return this
        if (m.range.last != n.length - 1 || m.value.isEmpty()) return this
        return Txt(src.substring(0, m.range.first))
    }
}

object CommandParser {
    private fun r(pattern: String) = Regex(pattern)

    // ---- limpeza do começo/fim da frase ----
    private val trailingPunct = r("[\\s.!?,;]+$")
    private val trailingPlease = r("\\s*,?\\s*(?:por favor|pfv|pf)$")
    private val wakeWord = r("^(?:(?:ok|ei|hey|oi|ola)\\s*,?\\s+)?punto\\s*,?\\s*")
    private val leadingPlease = r("^por favor\\s*,?\\s*")
    private val leadingCan = r("^(?:voce\\s+)?(?:pode|poderia|consegue|quero que voce)\\s+")

    // ---- regras, na ordem de prioridade ----
    private val help = r("\\b(?:ajuda|me ajuda|o que (?:voce )?(?:sabe|consegue|pode) fazer|quais (?:sao )?(?:os )?comandos|comandos)\\b")
    private val airCon = r("\\b(?:ar condicionado|ar-condicionado|climatizador|(?:liga|ligar|desliga|desligar|aumenta|diminui|abaixa)\\s+o\\s+ar)\\b")
    private val whatsapp = r(
        "\\b(?:manda|mande|mandar|envia|envie|enviar|escreve|escreva)\\s+(?:uma\\s+|um\\s+)?" +
            "(?:mensagem|msg|zap|zapzap|whats|whatsapp|recado)\\s+(?:(?:no|pelo)\\s+(?:whats|whatsapp|zap)\\s+)?" +
            "(?:(?:para|pra|pro|pa|ao|a|o)\\s+)+(.+?)" +
            "(?:\\s+(?:(?:dizendo|falando|escrevendo|avisando)(?:\\s+que)?|que)\\s+(.+))?$",
    )
    private val call = r("\\b(?:liga|ligar|ligue|telefona|telefonar|telefone)\\s+(?:(?:para|pra|pro|pa|a|o|no|na)\\s+)+(.+)$")
    private val navApp = r(
        "\\b(?:coloca|coloque|bota|bote|poe|ponha|abre|abra|abrir|usa|use)\\s+(?:no|o)\\s+" +
            "(?:waze|maps|mapa|google maps|gps)\\s+(?:(?:para|pra|pro|ate|em|no|na|o|a)\\s+)*(.+)$",
    )
    private val nav = r(
        "\\b(?:me leva|me leve|me levar|leva|leve|navegar|navega|navegue|tracar rota|traca uma rota|traca rota|" +
            "rota|caminho|como (?:eu )?chego|como chegar|como faco pra chegar|vamos|bora|quero ir|preciso ir|ir)\\s+" +
            "(?:(?:para|pra|pro|ate|ao|aos|a|as|em|no|na|nos|o|um|uma)\\s+)+(.+)$",
    )
    private val carData = r(
        "\\b(?:temperatura d[oa]s?\\s+(?:motor|carro|agua|radiador|oleo)|motor (?:esta|ta) quente|" +
            "nivel d[oe]\\s+(?:combustivel|gasolina|oleo|tanque)|quanto (?:de|tem de) (?:combustivel|gasolina)|" +
            "tanque|rpm|rotacao|giro do motor|bateria do carro|dados do carro|status do carro|como (?:esta|ta) o carro)\\b",
    )
    private val weather = r(
        "\\b(?:clima|previsao|vai chover|chover|chovendo|chuva|temperatura|calor|frio|graus|" +
            "tempo (?:la fora|hoje|agora)|como (?:esta|ta) o tempo)\\b",
    )
    private val time = r("\\b(?:que horas|que hora|horas sao|horario)\\b")
    private val speed = r("\\b(?:velocidade|quanto (?:eu )?(?:estou|to|tou) (?:andando|correndo|indo)|a quantos (?:km|quilometros))\\b")
    private val trip = r("\\b(?:distancia|quantos (?:km|quilometros) (?:eu )?(?:ja )?(?:andei|rodei)|quanto (?:eu )?(?:ja )?(?:andei|rodei)|viagem)\\b")
    private val volUp = r("\\b(?:aumenta|aumentar|aumente|sobe|subir|suba)\\s+(?:o\\s+)?(?:volume|som)\\b|\\bmais alto\\b")
    private val volDown = r("\\b(?:abaixa|abaixar|abaixe|diminui|diminuir|diminua|baixa|baixar|baixe)\\s+(?:o\\s+)?(?:volume|som)\\b|\\bmais baixo\\b")
    private val pause = r(
        "^(?:pausa|pausar|pause|para|parar|pare|silencio|desliga)(?:\\s+(?:a|o))?(?:\\s+(?:musica|som|audio|tudo))?$" +
            "|\\b(?:pausa|pausar|pause)\\b|\\b(?:para|parar|pare) (?:a|o) (?:musica|som)\\b",
    )
    private val next = r("\\b(?:proxima|pula|pular|pule|avanca|avancar|passa a musica|passa essa|troca a musica|troca de musica|outra musica)\\b")
    private val previous = r("\\b(?:anterior|volta a musica|voltar a musica|musica de antes|volta uma)\\b")
    private val resume = r("^(?:continua|continuar|continue|despausa|play|retoma|retomar|volta a tocar|solta o som|toca|tocar|toque|musica)(?:\\s+(?:a\\s+)?(?:musica|som))?$")
    private val music = r(
        "(?:^|\\s)(?:toca|tocar|toque|coloca|colocar|coloque|bota|botar|bote|poe|ponha|quero ouvir|ouvir|ouve|" +
            "play|escutar|quero escutar|reproduz|reproduzir)\\s+(.+)$",
    )
    private val open = r(
        "(?:^|\\s)(?:abre|abrir|abra|inicia|iniciar|inicie|executa|executar|entra no|entrar no|entra na|mostra|mostrar)\\s+" +
            "(?:o\\s+|a\\s+)?(?:app\\s+|aplicativo\\s+)?(?:do\\s+|da\\s+)?(.+)$",
    )

    // ---- limpeza do nome da música ----
    private val musicAppSuffix = r("(?:^|\\s+)(?:no|na|do|pelo|pela|usando o)\\s+(spotify|youtube music|youtube musica|youtube|yt music)$")
    private val musicPrefix = r(
        "^(?:(?:uma|um|a|o|as|os)\\s+)?(?:(?:musica|musicas|cancao|cancoes|playlist|album|disco|som|sons|banda|cantor|cantora|artista)\\s+)?" +
            "(?:(?:de|do|da|dos|das)\\s+)?",
    )
    private val genericMusic = setOf(
        "", "musica", "musicas", "uma musica", "alguma musica", "algo", "alguma coisa", "qualquer coisa",
        "um som", "som", "o som", "a musica",
    )

    fun parse(raw: String): Command {
        var t = Txt(Normalizer.normalize(raw.trim(), Normalizer.Form.NFC))
        t = t.stripEnd(trailingPunct).stripEnd(trailingPlease).stripEnd(trailingPunct)
        t = t.stripStart(wakeWord).stripStart(leadingPlease).stripStart(leadingCan)
        val n = t.n

        fun MatchResult.g(i: Int): String = groups[i]?.range?.let { t.slice(it).trim() } ?: ""

        if (n.isBlank()) return Command.Unknown(raw)
        help.find(n)?.let { return Command.Help }
        airCon.find(n)?.let { return Command.AirConditioning }
        whatsapp.find(n)?.let { return Command.WhatsApp(it.g(1), it.g(2)) }
        call.find(n)?.let { return Command.Call(it.g(1)) }
        navApp.find(n)?.let { return Command.Navigate(it.g(1)) }
        nav.find(n)?.let { return Command.Navigate(it.g(1)) }
        carData.find(n)?.let { return Command.CarData }
        weather.find(n)?.let { return Command.Weather }
        time.find(n)?.let { return Command.Time }
        speed.find(n)?.let { return Command.Speed }
        trip.find(n)?.let { return Command.Trip }
        volUp.find(n)?.let { return Command.VolumeUp }
        volDown.find(n)?.let { return Command.VolumeDown }
        pause.find(n)?.let { return Command.Pause }
        next.find(n)?.let { return Command.Next }
        previous.find(n)?.let { return Command.Previous }
        resume.find(n)?.let { return Command.Resume }
        music.find(n)?.let { return musicCommand(Txt(it.g(1))) }
        open.find(n)?.let { return Command.OpenApp(it.g(1)) }
        return Command.Unknown(t.src)
    }

    private fun musicCommand(query: Txt): Command {
        var q = query
        var app: String? = null
        musicAppSuffix.find(q.n)?.let { m ->
            app = if (m.groupValues[1] == "spotify") "spotify" else "ytmusic"
            q = Txt(q.src.substring(0, m.range.first))
        }
        if (q.n.trim() in genericMusic) {
            return if (app == null) Command.Resume else Command.PlayMusic("", app)
        }
        q = q.stripStart(musicPrefix)
        val text = q.src.trim()
        if (norm(text) in genericMusic) {
            return if (app == null) Command.Resume else Command.PlayMusic("", app)
        }
        return Command.PlayMusic(text, app)
    }
}
