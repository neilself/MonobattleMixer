import java.io.File

fun main(args: Array<String>) {
  MonobattleMixer().run()
}

fun debugPrintln(str: String) {
  if (debugMode) {
    println(str)
  }
}

const val debugMode: Boolean = false
const val playerFilename = "players.txt"
const val outputFilename = "teams.txt"
const val numMatchupsPerRound = 2
const val numPlayersPerMatchup = 8
const val numPlayersPerTeam = 4
const val numRounds = 16

class MonobattleMixer {

  fun run() {
    val playerNameList = File(playerFilename).readLines()
    val playerList: MutableList<Player> = playerNameList.map { Player(it) }.toMutableList()

    // Initialize match counting maps so that all pairing histories are at 0
    for (i in 0 until playerList.size - 1) {
      for (j in i + 1 until playerList.size) {
        playerList[i].matchCountingMap[playerList[j]] = 0
        playerList[j].matchCountingMap[playerList[i]] = 0
      }
    }

    // Each round is a pair of player lists (each player list being one team).
    val roundList = mutableListOf<List<Pair<List<Player>, List<Player>>>>()

    println("\n\nPlayers for round 0:\n\n${playerListToString(playerList)}")

    val builder = StringBuilder("\n====== Monobattle Mixer Teams ======\n")
    for (i in 1..numRounds) {
      println("")
      // Shuffle, then sort the player list based on games played
      playerList.shuffle()
      playerList.sortWith { o1, o2 -> o1.numGamesPlayed - o2.numGamesPlayed }

      // TODO: Should probably use an interface to make switching between algorithms less hacky
//      val teamPair = createTeamPairUsingSimpleGreedyAlgorithm(playerList, numPlayersPerRound, numPlayersPerTeam)
      val teamPairs = createTeamPairUsingStateSpaceExploration(playerList, numMatchupsPerRound, numPlayersPerMatchup,
        numPlayersPerTeam)
      println("Found ideal teamPairs:")
      builder.append("\n--- Round $i ---\n")
      for (j in teamPairs.indices) {
        val teamPair = teamPairs[j]
        println("teamPair: ${playerListToString(teamPair.first)} & ${playerListToString(teamPair.second)}")
        builder.append("(${j * 2 + 1}) ${playerListToSimpleString(teamPair.first)} vs (${j * 2 + 2}) ${
          playerListToSimpleString(teamPair
            .second)
        }\n")
      }

      incrementPairingsAndGamesPlayedForTeamPairs(teamPairs)
      roundList.add(teamPairs)
      println("\n\nPlayers for round $i:\n\n${playerListToString(playerList)}")
    }

    File(outputFilename).printWriter().use { out ->
      out.write(builder.toString())
    }
  }

  private fun createTeamPairUsingStateSpaceExploration(playerList: List<Player>, numMatchupsPerRound: Int,
    numPlayersPerMatchup: Int, numPlayersPerTeam: Int,
  ): List<Pair<List<Player>, List<Player>>> {
    val returnList = mutableListOf<Pair<List<Player>, List<Player>>>()
    for (i in 0 until numMatchupsPerRound) {
      val tempPlayerList = mutableListOf<Player>()
      tempPlayerList.addAll(playerList.subList(i * numPlayersPerMatchup, (i + 1) * numPlayersPerMatchup))

      val allPossibleTeamPairs = createListOfTeamPairs(tempPlayerList, createAllPossibleTeams(tempPlayerList,
        mutableListOf(),
        numPlayersPerTeam))
      debugPrintln("Number of possible team pairs: ${allPossibleTeamPairs.size}")
      var lowestScoringTeamPairSoFar = allPossibleTeamPairs[0]
      var lowestScoreSoFar = Int.MAX_VALUE
      for (teamPair in allPossibleTeamPairs) {
        val score = scoreTeamPair(teamPair)
        if (score < lowestScoreSoFar) {
          debugPrintln("New better score found: $score")
          lowestScoreSoFar = score
          lowestScoringTeamPairSoFar = teamPair
        }
      }

      returnList.add(lowestScoringTeamPairSoFar)
    }
    return returnList
  }

  private fun createListOfTeamPairs(playerList: List<Player>, teamList: List<List<Player>>): List<Pair<List<Player>,
          List<Player>>> {
    val returnList = mutableListOf<Pair<List<Player>, List<Player>>>()

    for (team in teamList) {
      var otherTeam = playerList.toMutableList()
      otherTeam = otherTeam.subtract(team).toMutableList()
      returnList.add(Pair(team, otherTeam))
    }

    return returnList
  }

  private fun createAllPossibleTeams(
    playerPoolList: List<Player>, oneTeamSoFar: MutableList<Player>,
    numPlayersPerTeam:
    Int,
  ):
          List<List<Player>> {
    // Where the magic happens. This is a recursive function that basically does a "N choose K".
    //
    // On the first level, playerPoolList is 'full' and oneTeamSoFar is empty. Each level, there's a loop where
    // you choose a player from playerPoolList and add it to oneTeamSoFar; at the same time, you remove that player,
    // as well as 'earlier' players from playerPoolList (because leaving them in will make the algorithm create
    // redundant teams). Base case is when you hit the desired number of players for a team. Eventually a list of teams
    // (each of which is a list of players) is returned. You don't need to return both teams needed in a pairing,
    // because you can always get the other team based on which players weren't chosen for oneTeamSoFar.
    //
    // NOTE: I wrote this method assuming that playerPoolList starts out on the first level with size
    //       (2 * numPlayersPerTeam). If that's not true, it probably breaks.

    if (oneTeamSoFar.size == numPlayersPerTeam) {
      return mutableListOf<List<Player>>(oneTeamSoFar)
    }

    val returnList = mutableListOf<List<Player>>()
    for (i in 0..playerPoolList.size - (numPlayersPerTeam - oneTeamSoFar.size)) {
      val player = playerPoolList[i]

      // Make copies of the two lists to do mutations on
      val playerPoolListCopy = mutableListOf<Player>()
      playerPoolListCopy.addAll(playerPoolList)
      val oneTeamSoFarCopy = mutableListOf<Player>()
      oneTeamSoFarCopy.addAll(oneTeamSoFar)

      // Shift one player from the player pool list to the team being steadily created
      oneTeamSoFarCopy.add(player)
      // Remove the player that was added, as well as earlier players from playerPoolList, from playerPoolListCopy
      playerPoolListCopy.removeAll(playerPoolListCopy.subList(0, i + 1))

      // Pass that down to a recursive call, and add what gets returned to what we'll eventually return
      returnList.addAll(createAllPossibleTeams(playerPoolListCopy, oneTeamSoFarCopy, numPlayersPerTeam))
    }

    return returnList
  }

  private fun areTeamPairsEquivalent(
    teamPairOne: Pair<List<Player>, List<Player>>,
    teamPairTwo: Pair<List<Player>, List<Player>>,
  )
          : Boolean {
    val nameSetPairOneFirst: MutableSet<String> = teamPairOne.first.map { it.name }.toMutableSet()
    val nameSetPairTwoFirst: MutableSet<String> = teamPairTwo.first.map { it.name }.toMutableSet()
    if (nameSetPairOneFirst != nameSetPairTwoFirst) {
      return false
    }

    val nameSetPairOneSecond: MutableSet<String> = teamPairOne.second.map { it.name }.toMutableSet()
    val nameSetPairTwoSecond: MutableSet<String> = teamPairTwo.second.map { it.name }.toMutableSet()

    return nameSetPairOneSecond == nameSetPairTwoSecond
  }

  private fun scoreTeamPair(teamPair: Pair<List<Player>, List<Player>>): Int {
    return scoreTeam(teamPair.first) + scoreTeam(teamPair.second)
  }

  private fun scoreTeam(team: List<Player>): Int {
    var score = 0
    for (i in 0 until team.size - 1) {
      for (j in i + 1 until team.size) {
        val scoreOrNull = team[i].matchCountingMap[team[j]]
        // TODO: There's probably a better way to do this null handling.
        if (scoreOrNull != null) {
          score += scoreOrNull
        } else {
          throw IllegalStateException("Match counting map for ${team[i].name} was in a bad state; could " +
                  "not find ${team[j].name}")
        }
      }
    }
    return score
  }

  private fun incrementPairingsAndGamesPlayedForTeamPairs(teamPairs: List<Pair<List<Player>, List<Player>>>) {
    for (teamPair in teamPairs) {
      incrementPairingsAndGamesPlayed(teamPair)
    }
  }

  private fun incrementPairingsAndGamesPlayed(teamPair: Pair<List<Player>, List<Player>>) {
    incrementPairingsAndGamesPlayed(teamPair.first)
    incrementPairingsAndGamesPlayed(teamPair.second)
  }

  private fun incrementPairingsAndGamesPlayed(list: List<Player>) {
    for (i in 0 until list.size - 1) {
      for (j in i + 1 until list.size) {
        list[i].matchCountingMap[list[j]] = list[i].matchCountingMap.getValue(list[j]) + 1
        list[j].matchCountingMap[list[i]] = list[j].matchCountingMap.getValue(list[i]) + 1
      }
    }
    for (player in list) {
      player.numGamesPlayed++
    }
  }

  private fun chooseLeastRepeatedPairing(list: List<Player>): Pair<Player, Player> {
    var lowestPairSoFar = Pair(list[0], list[1])
    var lowestCountSoFar = lowestPairSoFar.first.matchCountingMap[lowestPairSoFar.second]
      ?: throw IllegalStateException("Match counting map for players ${list[0]} and ${list[1]} was in a bad state")

    for (i in 0 until list.size - 1) {
      for (j in i + 1 until list.size) {
        val p1 = list[i]
        val p2 = list[j]
        val count = list[i].matchCountingMap[list[j]]
          ?: throw IllegalStateException("Match " +
                  "counting map for players ${list[0]} and ${list[1]} was in a bad state")
        if (count < lowestCountSoFar) {
          lowestPairSoFar = Pair(p1, p2)
          lowestCountSoFar = count
        }
      }
    }

    return lowestPairSoFar
  }

  private fun playerListToString(list: List<Player>): String {
    val newList = mutableListOf<Player>()
    newList.addAll(list)
    newList.sortWith { o1, o2 -> o1.name.compareTo(o2.name) }

    val builder = StringBuilder()
    builder.append("[")
    for (player in newList) {
      builder.append("\n${player}")
    }
    builder.append("\n]")
    return builder.toString()
  }

  private fun playerListToSimpleString(list: List<Player>): String {
    val newList = mutableListOf<Player>()
    newList.addAll(list)
    newList.sortWith { o1, o2 -> o1.name.compareTo(o2.name) }

    val builder = StringBuilder()
    builder.append("[")
    for (i in list.indices) {
      val player = list[i]
      builder.append(player.name)
      if (i != list.size - 1) {
        builder.append(", ")
      }
    }
    builder.append("]")
    return builder.toString()
  }

  fun findRedundantTeamPairs(teamPairList: List<Pair<List<Player>, List<Player>>>): Int {
    val listCopy = teamPairList.toMutableList()

    for (i in listCopy.indices.reversed()) {
      if (i == 0) {
        continue
      }
      for (j in listCopy.indices.reversed()) {
        if (j == i) {
          continue
        }

        val pairOne = teamPairList[i]
        val pairTwo = teamPairList[j]

        if (areTeamPairsEquivalent(pairOne, pairTwo)) {
          debugPrintln("Found equal team pairs: ${playerListToString(pairOne.first)} - ${
            playerListToString(pairOne
              .second)
          }")
          debugPrintln("with: ${playerListToString(pairTwo.first)} - ${
            playerListToString(pairTwo
              .second)
          }")
          listCopy.removeAt(i)
          break
        }
      }
    }

    return listCopy.size
  }

  fun createTeamPairUsingSimpleGreedyAlgorithm(
    playerList: List<Player>,
    numPlayersPerRound: Int,
    numPlayersPerTeam: Int,
  ): Pair<List<Player>,
          List<Player>> {
    // Create temp player list of whoever has played the least
    val tempPlayerList = mutableListOf<Player>()
    tempPlayerList.addAll(playerList.subList(0, numPlayersPerRound))

    tempPlayerList.shuffle()
    val otherTeam = mutableListOf<Player>()
    while (tempPlayerList.size > numPlayersPerTeam) {
      // Choose the pairing with the least repeats, add them to a team, then remove them from the temp player list
      val pairing = chooseLeastRepeatedPairing(tempPlayerList)
      otherTeam.add(pairing.first)
      otherTeam.add(pairing.second)
      tempPlayerList.remove(pairing.first)
      tempPlayerList.remove(pairing.second)
    }

    return Pair(tempPlayerList, otherTeam)
  }

  data class Player(val name: String) {
    val matchCountingMap: MutableMap<Player, Int> = mutableMapOf()
    var numGamesPlayed = 0

    override fun toString(): String {
      val builder = StringBuilder()
      builder.append("** $name[$numGamesPlayed]: ")
      for (key in matchCountingMap.keys) {
        builder.append("${key.name}(${matchCountingMap[key]}), ")
      }
      return builder.toString()
    }
  }
}