# Notice — modèle de reconnaissance faciale (EdgeFace)

## Modèle utilisé

- **Nom** : EdgeFace-S (variante `gamma = 0.5`), Idiap Research Institute.
- **Tâche** : extraction d'embeddings faciaux (512 dimensions), entraîné pour la vérification
  d'identité, format d'entrée ArcFace 112×112.
- **Source des poids** : export ONNX officiel dérivé des poids PyTorch Idiap
  (`https://github.com/yakhyo/edgeface-onnx`, tag `weights`,
  fichier `edgeface_s_gamma_05.onnx`, 14 805 514 octets).
- **Fichier embarqué** : `app/src/main/assets/edgeface_s.onnx`.
- **SHA-256** : `B850767CF791BDA585600B5C4C7D7432B2F998CCD862CAAE34EF1AFA967D2E54`
  (vérifié à chaque chargement ; un fichier différent est refusé).

## Licence des poids — CC BY-NC-SA 4.0

Les poids EdgeFace sont publiés par Idiap sous **Creative Commons
Attribution – Pas d'Utilisation Commerciale – Partage dans les Mêmes Conditions 4.0
International (CC BY-NC-SA 4.0)** :

- `https://gitlab.idiap.ch/bob/bob.paper.tbiom2023_edgeface/-/blob/master/LICENSE`
- `https://huggingface.co/Idiap/EdgeFace-Base` (métadonnée `license: cc-by-nc-sa-4.0`)

Conséquences pour ce projet :

1. **Attribution** : Idiap Research Institute doit rester crédité (ce fichier + citation du
   papier TBIOM 2024).
2. **Non commercial** : l'application est utilisée par un club sportif amateur, sans vente ni
   service payant. Toute réutilisation commerciale nécessiterait une autorisation d'Idiap.
3. **Partage à l'identique** : toute version modifiée du modèle (ré-entraînement, quantization,
   conversion) doit être diffusée sous la même licence.

Le code du dépôt d'export ONNX est sous licence MIT ; **la licence MIT ne couvre pas les
poids**, qui restent sous CC BY-NC-SA 4.0.

## Runtime

- **ONNX Runtime Mobile** (`com.microsoft.onnxruntime:onnxruntime-android`, MIT), 100 % local.
- Aucune image, frame, embedding ou métadonnée faciale n'est transmise : ni API, ni Supabase,
  ni analytics, ni logs. Seuls des embeddings chiffrés (AES-GCM 256, clé Keystore) sont stockés
  dans une base Room située dans `noBackupFilesDir` (exclue des sauvegardes et du transfert).

## Vérifications restant à faire avant activation du pointage facial

- [ ] Équivalence PyTorch ↔ ONNX : cosinus ≥ 0,99 sur 20 paires d'images.
- [ ] Latence sur la tablette cible, delegate GPU puis CPU (moyenne sur 100 inférences).
- [ ] Calibration des seuils (`seuilHaut`, `seuilBas`, `margeMin`) sur le groupe réel.
- [ ] Alignement ArcFace 112×112 à partir des landmarks ML Kit (pipeline caméra).
- [ ] Mesure température / batterie après 90 minutes d'utilisation continue.
