package com.spark.app.data;

public final class Curiosity {
    // Light, playful creative prompts - no theory, no homework. 3 minutes, one small spark.
    private static final String[] CARDS = {
        "Write a 3-line story that starts with a door that shouldn't be there.",
        "If your mood today were weather, what's the forecast?",
        "Name a band made of kitchen appliances. What's their hit song?",
        "Invent a word the world is missing right now. Define it.",
        "Write a 4-line poem about the last thing you drank.",
        "Redesign a bus stop so waiting feels like a game.",
        "What's the title of the movie of your week? One-line plot too.",
        "Combine two animals into one. Where does it live?",
        "Turn your to-do list into a treasure map. Where is the X?",
        "If colors had sounds, what does green sound like?",
        "Invent a tiny holiday. What do people do on it?",
        "Your future self texts you 3 words. What do they say?",
        "Make up a sport played with a spoon and a balloon. Rules?",
        "Caption a photo of today's sky like a nature documentary.",
        "You get one superpower, but it's useless. Make it sound epic.",
        "A robot asks you why sunsets matter. Answer in 2 lines.",
        "Design a flag for a country run by cats. Name 3 symbols.",
        "Write the menu for a restaurant run by your grandmother.",
        "What would a museum about your childhood put in the first glass case?",
        "Give your walk to the kitchen a movie-trailer voiceover.",
        "Invent a gadget that makes one boring chore 10% more fun.",
        "Describe your dream workspace in exactly 5 words.",
        "Rename the days of the week. What's your logic?",
        "Write a postcard from your dream city to your desk."
    };

    public static String daily() {
        int day = Dates.dayOfYear();
        return CARDS[Math.floorMod(day, CARDS.length)];
    }

    public static String random() {
        return CARDS[new java.util.Random().nextInt(CARDS.length)];
    }

    public static String[] all() { return CARDS; }
}
