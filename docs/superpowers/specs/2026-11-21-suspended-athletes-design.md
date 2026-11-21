# Design: Prise en charge du statut « Suspendu » dans l'application

Date: 2026-11-21

## Intent
Le statut `SUSPENDU` existe déjà dans `Member` et est synchronisé via Supabase, mais n'est traité que comme un badge. L'objectif est de l'intégrer dans tous les flux : pointage, filtres, SMS, statistiques, agenda de présence, tableau de bord et fiche athlète. Un athlète suspendu n'est jamais compté absent.

## Décision clé
- Pas de dates de suspension (option retenue par l'utilisateur) : la suspension est un statut global. Dans l'agenda de la fiche athlète, **tous** les jours d'entraînement sont colorés en noir pour un athlète suspendu.
- Les suspendus figurent dans la liste des non-pointés au pointage avec le badge « Suspendu ».

## Portée (surfaces touchées)

### A. Modèle & sync (aucun changement de schéma)
- `Member.status` déjà synchronisé comme entité `members`. Vérifier que le pull conserve `status`.

### B. Pointage coach (`CoachSessionPointageScreen`)
- Badge « Suspendu » dans la liste des non-pointés et dans `StatAthletesDialog`.
- À la validation : les suspendus ne sont pas enregistrés comme absents.
- Exclus des SMS de séance.

### C. Page Athlètes (`MembersScreens`)
- Nouveau chip filtre « Suspendus (N) » (toggle).
- Carte teintée rouge pour un suspendu (+ badge existant).

### D. Centre SMS (`AdminSmsCenterScreen`)
- Nouveau mode `SUSPENDUS` dans `SmsMode` + chip « Suspendus ».
- `memberPool = allMembers.filter { it.isSuspended }` pour ce mode uniquement ; les autres modes gardent leur exclusion.

### E. Statistiques (`StatFilterModels` + `AdminStatsScreens`)
- Nouveau `StatFilterMode.SUSPENDUS` (couleur `JudoRed`), filtrage `isSuspended`.

### F. Agenda de la fiche athlète (`MemberAttendanceCalendar`)
- Si `member.isSuspended` : jours d'entraînement colorés en noir (texte blanc) + badge, exclus du comptage mensuel d'absences.
- Entrée de légende « Suspendu » (noir).

### G. Tableau de bord admin (`AdminDashboardScreen`)
- Compteur « Suspendus » ajouté ; exclus d'« actifs » (déjà le cas).

### H. Fiche athlète (`MemberDetailScreen` / `MemberBeltHeader`)
- Badge « Suspendu » rouge à côté de la ceinture/catégorie.

### I. Catégories admin (`AdminCategoriesScreen`)
- Compte « suspendus » affiché par catégorie (optionnel).

### J. Alertes d'absences (`AbsenceAlerts`)
- Déjà OK (`status != "ACTIF"` skip).

## Anti-goals
- Pas de dates de suspension, pas de migration Room.
- Pas de modification du schéma Supabase.

## Décisions laissées au builder
- Couleur exacte du rouge pour les cartes (réutiliser `JudoRed`).
- Ordre des chips de filtre.
- Libellé exact du badge dans l'agenda (« Suspendu »).
