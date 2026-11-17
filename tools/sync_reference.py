"""Refresh official zone names and a live API fixture used only by tests."""
from pathlib import Path
from html.parser import HTMLParser
from datetime import datetime, timezone
import json
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PORTAL = 'https://www.e-solat.gov.my/'
API = PORTAL + 'index.php?r=esolatApi/takwimsolat&period=year&zone=WLY01'
STATES = {'JHR':'Johor','KDH':'Kedah','KTN':'Kelantan','MLK':'Melaka','NGS':'Negeri Sembilan','PHG':'Pahang','PLS':'Perlis','PNG':'Pulau Pinang','PRK':'Perak','SBH':'Sabah','SGR':'Selangor','SWK':'Sarawak','TRG':'Terengganu','WLY':'Wilayah Persekutuan'}


class Zones(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.code = None
        self.parts = []
        self.items = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'option' and attrs.get('class') == 'hs':
            self.code = attrs['value']
            self.parts = []

    def handle_data(self, data):
        if self.code:
            self.parts.append(data)

    def handle_endtag(self, tag):
        if tag == 'option' and self.code:
            name = ''.join(self.parts).strip().split(' - ', 1)[-1]
            self.items.append(dict(code=self.code, state=STATES[self.code[:3]], name=name))
            self.code = None


def main():
    parser = Zones()
    parser.feed(urllib.request.urlopen(PORTAL, timeout=30).read().decode())
    assert len(parser.items) >= 50
    target = ROOT / 'app/src/main/assets/zones.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(dict(source=PORTAL, retrievedAt=datetime.now(timezone.utc).isoformat(), zones=parser.items), ensure_ascii=False, indent=2), encoding='utf-8')
    data = json.load(urllib.request.urlopen(API, timeout=30))
    assert data['zone'] == 'WLY01' and len(data['prayerTime']) >= 365
    fixture = ROOT / 'app/src/test/resources/jakim-wly01-year.json'
    fixture.parent.mkdir(parents=True, exist_ok=True)
    fixture.write_text(json.dumps(data, ensure_ascii=False), encoding='utf-8')
    print(f'Saved {len(parser.items)} official zones and {len(data["prayerTime"])} prayer dates.')


if __name__ == '__main__':
    main()
