"""Writes Audius API v1 response fixtures (shapes follow the public API; all content is fictional).
Regenerate: python3 tests/fixtures/audius/make_fixtures.py tests/fixtures/audius"""
import json, os, sys

out = sys.argv[1]
NODE = "https://creatornode2.audius.co/content"

def art(cid, sizes=("150x150", "480x480", "1000x1000")):
    a = {s: f"{NODE}/{cid}/{s}.jpg" for s in sizes}
    a["mirrors"] = ["https://creatornode3.audius.co", "https://audius-content-12.cultur3stake.com"]
    return a

def user(uid, name, handle, followers, verified=False, deactivated=False, picture_sizes=("150x150", "480x480", "1000x1000")):
    return {
        "album_count": 1, "artist_pick_track_id": None, "bio": "Field recordings and slow synths.",
        "cover_photo": {"640x": f"{NODE}/cover{uid}/640x.jpg", "2000x": f"{NODE}/cover{uid}/2000x.jpg"},
        "followee_count": 52, "follower_count": followers, "does_follow_current_user": False,
        "handle": handle, "id": uid, "is_verified": verified, "twitter_handle": None, "instagram_handle": None,
        "tiktok_handle": None, "verified_with_twitter": False, "verified_with_instagram": False,
        "verified_with_tiktok": False, "website": "", "donation": "", "location": "Lisbon, Portugal",
        "name": name, "playlist_count": 2,
        "profile_picture": None if picture_sizes is None else art(f"bafypic{uid.lower()}", picture_sizes),
        "repost_count": 7, "track_count": 14, "is_deactivated": deactivated, "is_available": not deactivated,
        "erc_wallet": "0x" + "1" * 40, "spl_wallet": "9" * 44, "spl_usdc_payout_wallet": None,
        "supporter_count": 0, "supporting_count": 0, "total_audio_balance": 0, "wallet": "0x" + "1" * 40,
    }

MARLOW = user("nlGNe", "Marlow Fields", "marlowfields", 11141, verified=True)
JUNIPER = user("eAZl3", "Juniper Static", "juniperstatic", 2380)
ADA = user("Pd8xQ", "Ada North", "adanorth", 640)
GONE = user("q3RtZ", "Retired Account", "retired", 12, deactivated=True)

def track(tid, title, who, duration, genre="Electronic", mood="Peaceful", artwork=True, sizes=("150x150", "480x480", "1000x1000"),
          plays=1000, backlink=None, **flags):
    t = {
        "artwork": art(f"bafyart{tid.lower()}", sizes) if artwork else None,
        "description": "Recorded on a night ferry.", "genre": genre, "id": tid,
        "track_cid": "baeaaaiqsea" + tid.lower() * 3, "preview_cid": None, "orig_file_cid": "baeaaaiqseb" + tid.lower() * 3,
        "orig_filename": f"{title}.wav", "is_original_available": False, "mood": mood,
        "release_date": "2025-03-14T00:00:00Z", "remix_of": {"tracks": None}, "repost_count": 37, "favorite_count": 211,
        "comment_count": 4, "tags": "ambient,night,ferry", "title": title, "user": who, "duration": duration,
        "is_downloadable": False, "play_count": plays, "permalink": f"/{who['handle']}/{title.lower().replace(' ', '-').replace('(', '').replace(')', '').replace('.', '')}",
        "is_streamable": True, "ddex_app": None, "playlists_containing_track": [], "pinned_comment_id": None,
        "album_backlink": backlink, "access": {"stream": True, "download": False}, "is_stream_gated": False,
        "stream_conditions": None, "is_download_gated": False, "download_conditions": None, "is_delete": False,
        "is_unlisted": False, "is_available": True,
    }
    t.update(flags)
    return t

LOW_TIDE = track("Lb9Ey", "Low Tide Signals", MARLOW, 213, plays=48211)
PAPER = track("7eP5n", "Paper Lanterns", JUNIPER, 201, genre="Lo-Fi", mood="Romantic", sizes=("150x150", "480x480"),
              plays=9120, backlink={"playlist_id": 88231, "playlist_name": "Night Ferries", "permalink": "/juniperstatic/album/night-ferries-88231"})
NORTH = track("Qx2Wm", "Northbound (feat. Ada North)", MARLOW, 187.4, genre=None, mood="Energizing", artwork=False, plays=3100)
NOT_STREAMABLE = track("Ab3Cd", "Vault Copy", JUNIPER, 240, is_streamable=False)
DELETED = track("Dl7Tq", "Removed Upload", MARLOW, 120, is_delete=True)
GATED = track("Gt5Pp", "Supporters Only", ADA, 199, is_stream_gated=True, access={"stream": False, "download": False},
              stream_conditions={"usdc_purchase": {"price": 199, "splits": {}}})
ORPHAN = track("Or4Ph", "Old Demo", GONE, 95)
GLASS = track("Gh6Lk", "Glasshouse", ADA, 176, genre="Electronic", mood="Melancholy", plays=7004)
OVERPASS = track("Ov8Bm", "Overpass Bloom", JUNIPER, 224, genre="Lo-Fi", plays=5320)
ALBUM_TWO = track("Nf2Tw", "Harbour Clock", JUNIPER, 189, genre="Lo-Fi", plays=4410)

def write(name, payload):
    with open(os.path.join(out, name), "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=1, ensure_ascii=False)
        f.write("\n")

write("hosts.json", {"data": [
    "https://discoveryprovider.audius.co", "https://discoveryprovider2.audius.co/", "https://discoveryprovider3.audius.co",
    "https://audius-discovery-1.altego.net", "https://dn1.monophonic.digital", "https://audius-dp.singapore.creatorseed.com",
    "http://cleartext.example.net", "https://discoveryprovider.audius.co", "not a url", "https://dn.example.org/v1/path", 42]})
write("trending.json", {"data": [LOW_TIDE, PAPER, NOT_STREAMABLE, DELETED, GATED, ORPHAN, NORTH, LOW_TIDE, GLASS]})
write("search_tracks.json", {"data": [GLASS, NOT_STREAMABLE, OVERPASS]})
write("track.json", {"data": PAPER})
write("track_gated.json", {"data": GATED})
write("user_tracks.json", {"data": [LOW_TIDE, NORTH, DELETED]})
write("genre_trending.json", {"data": [GLASS, LOW_TIDE, OVERPASS, PAPER]})
write("playlist_tracks.json", {"data": [PAPER, ALBUM_TWO, OVERPASS]})
write("users_search.json", {"data": [
    MARLOW, user("Tz1Qa", "Tidal Office", "tidaloffice", 87, picture_sizes=("150x150",)), GONE,
    user("Np0Pc", "No Picture", "nopicture", 3, picture_sizes=None)]})

def playlist(pid, name, who, count, album=False, **flags):
    p = {
        "artwork": art(f"bafypl{pid.lower()}"), "description": "Late trains and harbour lights.", "permalink": f"/{who['handle']}/{'album' if album else 'playlist'}/{name.lower().replace(' ', '-')}-{pid}",
        "id": pid, "is_album": album, "is_image_autogenerated": False, "playlist_name": name, "playlist_contents":
        [{"track_id": t, "timestamp": 1741910400, "metadata_timestamp": 1741910400} for t in ["7eP5n", "Nf2Tw", "Ov8Bm"]][:count],
        "repost_count": 12, "favorite_count": 48, "total_play_count": 18800, "user": who, "ddex_app": None,
        "access": {"stream": True, "download": False}, "upc": None, "track_count": count, "is_delete": False, "is_private": False,
        "is_stream_gated": False, "stream_conditions": None,
    }
    p.update(flags)
    return p

ALBUM = playlist("DOPRl", "Night Ferries", JUNIPER, 3, album=True)
MIX = playlist("Kq9Zx", "Harbour Hours", MARLOW, 2)
write("playlists_search.json", {"data": [
    ALBUM, MIX, playlist("Em0Ty", "Empty Shelf", ADA, 0), playlist("Xd3Lt", "Gone Mix", ADA, 2, is_delete=True),
    playlist("Pv7Rt", "Private Sketches", ADA, 2, is_private=True),
    playlist("Pq1Gd", "Paid Album", ADA, 2, album=True, is_stream_gated=True, access={"stream": False, "download": False})]})
write("playlists_trending.json", {"data": [MIX, ALBUM]})
print(sorted(os.listdir(out)))
