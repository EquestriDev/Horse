# Horse
*Claim horses, view their stats, and bring them to you on demand.*

- Spigot version **1.12.2**
- Java version **1.8**
- Build system **Maven**

## Challenges
Despite the reduced set of features of this plugin and the resulting temptation to take the quickest and easiest route, there is at least one technical challenge in the way, which makes the implementation all the more intriguing. Said difficulty being the teleportation of entities which may or may not be anywhere in the world, including unloaded chunks.

Said issue was actually discussed on the [SpigotMC forums](https://www.spigotmc.org/threads/getting-entity-problem.332246/) not too long ago, and I voiced my opinion right there that such entities should never be stored to disk to begin with. One risks either duplicating said entity by spawning it in while it also resides in a saved chunk.

On the other hand, it is possible to load the chunk on demand to force the entity back into memory, and teleport it then, but this approach yields a whole battery of issues. Loading chunks manually is, of course, very slow, and will lag the server. How do you know where the entity was last seen? What to do if you load the chunk but it isn't there? A simple server crash or other error could easily trigger such an inconsistency. Therefore, this problem needs to be addressed from the start, in the design phase of the plugin.

A third alternative is to keep chunks loaded while there's a horse in them. This concept however cannot withstand critical examination. Forcing chunks to stay in memory causes excessive RAM usage. Furthermore, all chunks are lost when the server restarts anyway. Clearly, this is not a long-term solution.

## The existing plugin
My research while playing on your server and talking with some of the moderators has yielded that a duplication glitch with horses is fairly commonplace. As described above, I assume that horses get duplicated by server crashes and similar errors and the plugin has no mechanism built into it which could detect and remedy such a situation. So whenever it happens, manual staff interference is mandatory, whereas my solution will prevent this from ever happening. Shielding players from glaring bugs like that is very important in my opinion, as doing so reduces their insecurity while attempting to spot how something is supposed to work.

## Solution
Therefore, my plugin takes a somewhat more involved approach. I consider it best practice when tackling the aforementioned issue. Claiming horses is the main feature of EquestriWorlds, and using an advanced system is absolutely appropriate, as this is a feature we want to run smoothly.

Horse entities are spawned in sporadically where they are either requested or last left off, and are never saved to disk. This plugin keeps track of all their statistics and properties, and does a bit of extra work to make sure horse entities appear seamlessly where players would expect them. Additionally, it can effortlessly teleport them to the player, with remarkably little overhead.

Horse entities are marked with scoreboard tags, which are persistent, so we can easily tell if an entity represents a claimed horse. Furthermore, this approach also allows us to notice invalid entities which were saved to disk as the result of a previous crash or other error, which would usually result in horse duplication, and remove said entities without anyone noticing.

## Conclusion
So this plugin takes an approach which prevents errors such as horse duplication, is easy on the memory requirements, and puts players' minds at ease because everything "just works", from their perspectives. Upon reviewing the plugin and its source, I'm confident that you will agree with me.

## Future directions
I checked out the existing plugin in action and noticed that it makes extensive use of chest menus, which I'm a huge fan of. I was actually tempted to add chest menus to this plugin, but didn't want to let my ambitions get the better of me. Beyond that, this plugin is well ready to give horses additional properties and build it up to a gameplay enhancing feature plugin.