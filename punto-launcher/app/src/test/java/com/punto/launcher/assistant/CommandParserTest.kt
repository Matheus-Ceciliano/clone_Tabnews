package com.punto.launcher.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {
    private fun p(text: String) = CommandParser.parse(text)

    @Test fun music() {
        assertEquals(Command.PlayMusic("Legião Urbana", null), p("Toca Legião Urbana"))
        assertEquals(Command.PlayMusic("Evidências", null), p("coloca a música Evidências"))
        assertEquals(Command.PlayMusic("Henrique e Juliano", null), p("quero ouvir as músicas do Henrique e Juliano"))
        assertEquals(Command.PlayMusic("Anitta", "ytmusic"), p("toca Anitta no YouTube Music"))
        assertEquals(Command.PlayMusic("rock nacional", "spotify"), p("Ok Punto, toca rock nacional no Spotify por favor"))
        assertEquals(Command.PlayMusic("", "spotify"), p("toca no Spotify"))
        assertEquals(Command.Resume, p("toca uma música"))
        assertEquals(Command.Resume, p("continua"))
    }

    @Test fun mediaControls() {
        assertEquals(Command.Pause, p("pausa"))
        assertEquals(Command.Pause, p("para a música"))
        assertEquals(Command.Next, p("próxima"))
        assertEquals(Command.Next, p("pula essa"))
        assertEquals(Command.Previous, p("música anterior"))
        assertEquals(Command.VolumeUp, p("aumenta o volume"))
        assertEquals(Command.VolumeDown, p("abaixa o som"))
    }

    @Test fun navigation() {
        assertEquals(Command.Navigate("Avenida Paulista, 1000"), p("Me leva para a Avenida Paulista, 1000"))
        assertEquals(Command.Navigate("casa"), p("me leva pra casa"))
        assertEquals(Command.Navigate("shopping Iguatemi"), p("navegar até o shopping Iguatemi"))
        assertEquals(Command.Navigate("posto mais próximo"), p("Me leva pro posto mais próximo"))
        assertEquals(Command.Navigate("São José dos Campos"), p("como chego em São José dos Campos?"))
        assertEquals(Command.Navigate("Rua Augusta 500"), p("coloca no Waze Rua Augusta 500"))
        assertEquals(Command.Navigate("trabalho"), p("vamos para o trabalho"))
    }

    @Test fun info() {
        assertEquals(Command.Weather, p("Como está o clima?"))
        assertEquals(Command.Weather, p("vai chover hoje?"))
        assertEquals(Command.Weather, p("qual a temperatura lá fora"))
        assertEquals(Command.CarData, p("qual a temperatura do motor?"))
        assertEquals(Command.Time, p("que horas são"))
        assertEquals(Command.Speed, p("qual a minha velocidade?"))
        assertEquals(Command.Trip, p("quantos quilômetros eu andei"))
        assertEquals(Command.Help, p("o que você sabe fazer?"))
        assertEquals(Command.AirConditioning, p("liga o ar"))
    }

    @Test fun contacts() {
        assertEquals(Command.WhatsApp("Ana", "estou chegando"), p("manda um zap pra Ana dizendo que estou chegando"))
        assertEquals(Command.WhatsApp("minha mãe", "já saí"), p("Mande mensagem para minha mãe que já saí"))
        assertEquals(Command.WhatsApp("João", ""), p("manda mensagem pro João"))
        assertEquals(Command.Call("Pedro"), p("liga para o Pedro"))
    }

    @Test fun apps() {
        assertEquals(Command.OpenApp("Netflix"), p("abre a Netflix"))
        assertEquals(Command.OpenApp("Spotify"), p("abrir o app do Spotify"))
        assertEquals(Command.OpenApp("Waze"), p("abre o Waze"))
    }
}
