# Horse

The purpose of this plugin is a way for players to claim horses, warp claimed horses to them, and get additional information about them, via commands.  There is a certain temptation to take the easy route and implement the requested commands in the quickest and most obvious way.  However, thorough research on your server has revealed a few caveats which I'm hoping to address with my particular approach.

- Claimed horses tend to get **duplicated**, especially when there is a server crash.
- Even without duplication issues, keeping track of entities globally poses myriad technical challenges.
- Horses and other entities get unloaded from memory and stored to disk once they enter an empty chunk.
- Finding out where in world the horse was left off is impossible.
- Storing all horse locations and loading the chunk on demand causes server lag and is unreliable.
- Keeping references to horse objects risks severe **memory leaks**.
- Keeping chunks of all horses loaded will cause high memory usage and is doomed to fail once the server restarts or crashes.

Therefore, this plugin takes a different approach which I consider best practice for the way horses are intended to be used on your server.  There was actually a [thread on the Spigot forums](https://www.spigotmc.org/threads/getting-entity-problem.332246/) not too long ago, where this exact topic was discussed.  How to manage entities which you intend to teleport around while they may be in an unloaded chunk.  I expressed the same opinion there.

In the case of this plugin, I have come to this conclusion after a lot of research and with the future needs of your server in mind.  After all, your plans are quite elaborate and require a sophisticated system to keep track of horses without error.  I hope that after reviewing this plugin, you will agree with me.  Here is how it works.
- No claimed horse is ever stored as an entity on disk.
- The plugin will add the horse to the world and remove it again as needed.
- This process happens seemlessly to players.

There are several **benefits** to this:
- No stray horses are every found in the world under normal circumstances.
- Should such an invalid horse be found, e.g. after a crash, the plugin will safely remove it.
- No references to existing horses are ever kept. Thus, no memory leaks.
- New attributes or abilities can be added to the stored horses at any time.

All of this may seem a little excessive for this little proof of concept snippet, but it is necessary to ensure proper teleportation of horses, make sure the plugin is future proof.

Another design decision is keeping the identification of horses lenient.  This means that horses can be addressed by their name or index via commands.  The plugin will figure out which was intended.