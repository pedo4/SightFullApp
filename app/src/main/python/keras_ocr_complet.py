import gtts

def main(vocalText, vocalPath):

  tts = gtts.gTTS(text=vocalText, lang="it")
  tts.save(vocalPath)

  



