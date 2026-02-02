Design a email template with the following:

The colours:

* Neon Lime #A8FF00
* Shell Blue #3387FA
* Cool Grey #343434
* Close Black #1E1E1E
* White #FFFFFF

The fonts:

* For titles:
    * Fugaz One fully capitalized (90pt)
* For heading 1:
    * Fugaz one (fully capitalized 38PT (1/3 bigger than main text)
* For main text
    * Barlow Semi condensed light (26pt))
* Heading 2:
    * Same size as main text (26pt)

There is one logo image:

* ${appUrl} + '/api/assets/Logo.png'

There are multiple backgrounds:

* '${appUrl}/api/assets/BackdropBlack.jpg'
* '${appUrl}/api/assets/BackdropWhite.jpg'
* '${appUrl}/api/assets/BackdropBlue.jpg'

The email template must be switcheable between the black and white backgrounds for light and dark mode.
You must make use of the White and Close black for the fonts.
And further, behind the background images you must also put the opposite colour such that if no image is rendered it is
still legibile.

The email must have different icons for socials at the bottom as follows, there is no need to use the same formatting
for it.
<td style="padding: 5px 4px;">
<a href="https://discord.gg/dFam2yqXu7"
   target="_blank"
   style="color: #fbfafa; text-decoration: none;">
    <img src="https://cdn-icons-png.flaticon.com/32/2111/2111370.png"
         width="32"
         height="32"
         alt="Discord"
         border="0"
         style="display: block;">
</a>
</td>
<td style="padding: 5px 4px;">
<a href="https://www.facebook.com/BlueshellEsports"
   target="_blank"
   style="color: #fbfafa; text-decoration: none;">
    <img src="https://cdn-icons-png.flaticon.com/32/733/733547.png"
         width="32"
         height="32"
         alt="Facebook"
         border="0"
         style="display: block;">
</a>
</td>
<td style="padding: 5px 4px;">
<a href="https://www.instagram.com/esablueshell/"
   target="_blank"
   style="color: #fbfafa; text-decoration: none;">
    <img src="https://cdn-icons-png.flaticon.com/32/2111/2111463.png"
         width="32"
         height="32"
         alt="Instagram"
         border="0"
         style="display: block;">
</a>
</td>
<td style="padding: 5px 4px;">
<a href="https://twitter.com/BlueshellESA"
   target="_blank"
   style="color: #fbfafa; text-decoration: none;">
    <img src="https://cdn-icons-png.flaticon.com/32/733/733579.png"
         width="32"
         height="32"
         alt="Twitter"
         border="0"
         style="display: block;">
</a>
</td>
<td style="padding: 5px 4px;">
<a href="https://www.twitch.tv/blueshellesports"
   target="_blank"
   style="color: #fbfafa; text-decoration: none;">
    <img src="https://cdn-icons-png.flaticon.com/32/5968/5968819.png"
         width="32"
         height="32"
         alt="Twitch"
         border="0"
         style="display: block;">
</a>
</td>
<td style="padding: 5px 4px;">
<a href="https://esa-blueshell.nl/"
   target="_blank"
   style="color: #fbfafa; text-decoration: none;">
    <img src="https://cdn-icons-png.flaticon.com/32/1006/1006771.png"
         width="32"
         height="32"
         alt="Website"
         border="0"
         style="display: block;">
</a>
</td>

Further it must have a sent from and sent by at the bottom of the email:
<p style="margin: 0; color: #6c757d; font-size: 12px;">
This email was sent to: <strong th:text="${sentTo ?: to}">[recipient-email]</strong>
</p>
<p style="margin: 5px 0 0 0; color: #6c757d; font-size: 12px;">
Sent on: <span th:text="${#temporals.format(#temporals.createNow(), 'MMMM dd, yyyy \'at\' HH:mm')}">Date and Time</span>
</p>

The logo of the association must be at the top, followed by a main title which can be passe dinto the template.
If the main title is not passed in, don't display anything there.
The main title must use the shell blue colour, and the title font.
The main body of the email must start with "Dear {fullName}," and end with "Kind regards, Blueshell SiteCie"

The content of the email must be passed in by thymeleaf as html.
