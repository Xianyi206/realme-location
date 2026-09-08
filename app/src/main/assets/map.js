'use strict';
const statusLine = document.getElementById('network');
const coordinates = document.getElementById('coordinates');
const choose = document.getElementById('choose');
let selected = null;
let marker = null;
const params = new URLSearchParams(location.search);
const latitude = Number(params.get('lat'));
const longitude = Number(params.get('lng'));
const valid = (lat, lng) => Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
const initial = params.has('lat') && params.has('lng') && params.get('lat').trim() !== '' && params.get('lng').trim() !== '' && valid(latitude, longitude);
const map = L.map('map', { zoomControl: true, worldCopyJump: true }).setView(initial ? [latitude, longitude] : [32, 105], initial ? 16 : 4);
const tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  referrerPolicy: 'strict-origin-when-cross-origin'
});
let loaded = false;
let timer;
function failed() {
  statusLine.textContent = '地图暂不可用。可返回手动输入坐标，或切换网络后重试。';
  statusLine.classList.add('error');
}
tiles.on('tileerror', failed);
tiles.on('tileload', () => {
  loaded = true; clearTimeout(timer);
  statusLine.textContent = '地图坐标为 WGS84；请放大确认位置。';
  statusLine.classList.remove('error');
});
tiles.addTo(map);
timer = setTimeout(() => { if (!loaded) failed(); }, 10000);
function select(lat, lng) {
  // Wrap map copies to geographic longitude range.
  lng = ((lng + 180) % 360 + 360) % 360 - 180;
  if (!valid(lat, lng)) return;
  selected = { lat, lng };
  if (marker) marker.setLatLng([lat, lng]);
  else marker = L.marker([lat, lng], { icon: L.divIcon({ className: 'point-icon', iconSize: [24, 24], iconAnchor: [12, 12] }), keyboard: false }).addTo(map);
  coordinates.textContent = `纬度 ${lat.toFixed(6)} · 经度 ${lng.toFixed(6)}`;
  choose.disabled = false;
}
if (initial) select(latitude, longitude);
map.on('click', event => {
  const rect = document.getElementById('map').getBoundingClientRect();
  const x = rect.left + event.containerPoint.x;
  const y = rect.top + event.containerPoint.y;
  const visibleTile = [...document.querySelectorAll('.leaflet-tile-loaded')].some(tile => {
    const bounds = tile.getBoundingClientRect();
    return tile.naturalWidth > 0 && x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom;
  });
  if (!visibleTile) {
    statusLine.textContent = '该处地图尚未加载，请稍等、切换网络，或返回输入坐标。';
    statusLine.classList.add('error');
    return;
  }
  select(event.latlng.lat, event.latlng.lng);
});
choose.addEventListener('click', () => {
  if (selected) location.href = `locationpicker://select?lat=${selected.lat.toFixed(6)}&lng=${selected.lng.toFixed(6)}`;
});
window.addEventListener('resize', () => map.invalidateSize());
