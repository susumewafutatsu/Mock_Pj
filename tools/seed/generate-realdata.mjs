// tools/seed/generate-realdata.mjs
// Sinh bộ DỮ LIỆU THẬT cho nền tảng ôn thi JLPT.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.resolve(HERE, '../../src/main/resources/db/changelog/v2.0.0/realdata');

// Mật khẩu chung của mọi tài khoản mẫu.
const PASSWORD_HASH = '$2a$10$055Q9MxOWQJSgq3WkkgYkuDl/BQ76SoZD0/h15F31IgFq8ck72or.';
const EMAIL_DOMAIN = 'tangthucac.test';

// Cấp JLPT có sẵn trong DB (v1.0.4, không gắn context nên luôn tồn tại).
const LV = { N5: 1, N4: 2, N3: 3, N2: 4, N1: 5 };
const LEVEL_ORDER = { 1: 5, 2: 4, 3: 3, 4: 2, 5: 1 }; // LevelID → "số N" để so khó/dễ

// ═══════════════════════════ Tiện ích ═══════════════════════════

function mulberry32(seed) {
  let a = seed;
  return () => {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rand = mulberry32(20260913);
const pick = (arr) => arr[Math.floor(rand() * arr.length)];
const between = (lo, hi) => lo + rand() * (hi - lo);
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

class Raw { constructor(sql) { this.sql = sql; } }
/** Mốc thời gian tương đối: số phút TRƯỚC lúc chạy (âm = trong tương lai). */
const ago = (minutes) => {
  const m = Math.round(minutes);
  return new Raw(m >= 0 ? `NOW() - INTERVAL ${m} MINUTE` : `NOW() + INTERVAL ${-m} MINUTE`);
};
const DAY = 1440;

function lit(v) {
  if (v === null || v === undefined) return 'NULL';
  if (v instanceof Raw) return v.sql;
  if (typeof v === 'boolean') return v ? '1' : '0';
  if (typeof v === 'number') return String(v);
  const s = String(v);
  if (s.includes(';')) throw new Error(`Nội dung có dấu ";" (Liquibase sẽ cắt câu lệnh sai): ${s.slice(0, 80)}`);
  if (s.includes('--')) throw new Error(`Nội dung có "--": ${s.slice(0, 80)}`);
  return `'${s.replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
}

const statements = [];
function comment(text) { statements.push({ comment: text }); }
function insert(table, columns, rows, chunk = 150) {
  for (let i = 0; i < rows.length; i += chunk) {
    const part = rows.slice(i, i + chunk);
    const values = part.map((r) => `  (${columns.map((c) => lit(r[c])).join(', ')})`).join(',\n');
    statements.push({ sql: `INSERT INTO ${table} (${columns.join(', ')}) VALUES\n${values}` });
  }
}
function raw(sql) { statements.push({ sql }); }

// ═══════════════════════════ Tài khoản ═══════════════════════════

const slug = (name) => name.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D')
  .toLowerCase().split(/\s+/);
const emailOf = (name) => { const p = slug(name); return `${p[p.length - 1]}.${p[0]}@${EMAIL_DOMAIN}`; };

const ADMIN = { id: 'tt-admin-01', name: 'Phạm Minh Quân', role: 'ADMIN', createdDays: 120 };
const TEACHERS = [
  { id: 'tt-gv-01', name: 'Nguyễn Thu Hà', role: 'TEACHER', createdDays: 110, note: 'N3 · N2' },
  { id: 'tt-gv-02', name: 'Trần Đức Anh', role: 'TEACHER', createdDays: 105, note: 'N4' },
  { id: 'tt-gv-03', name: 'Lê Thị Mai Phương', role: 'TEACHER', createdDays: 100, note: 'N5 · chữ Hán' },
  { id: 'tt-gv-04', name: 'Hoàng Văn Kiên', role: 'TEACHER', createdDays: 9, note: 'mới vào' },
];
// ability: xác suất làm đúng câu vừa sức — dùng để sinh bài làm có điểm phân tán tự nhiên.
const STUDENTS = [
  ['01', 'Đỗ Minh Khang', 5, 0.55], ['02', 'Vũ Ngọc Ánh', 5, 0.74], ['03', 'Bùi Gia Huy', 5, 0.40],
  ['04', 'Đặng Thảo Vy', 5, 0.86], ['05', 'Ngô Quốc Bảo', 5, 0.66], ['06', 'Lý Hải Yến', 5, 0.48],
  ['07', 'Mai Tuấn Kiệt', 5, 0.70], ['08', 'Phan Khánh Linh', 5, 0.60],
  ['09', 'Trịnh Hoàng Nam', 4, 0.66], ['10', 'Hồ Bảo Ngọc', 4, 0.78], ['11', 'Dương Thành Đạt', 4, 0.52],
  ['12', 'Tạ Minh Thư', 4, 0.88], ['13', 'Cao Đức Mạnh', 4, 0.45], ['14', 'Lâm Quỳnh Như', 4, 0.70],
  ['15', 'Kiều Anh Tú', 4, 0.60], ['16', 'Chu Thị Hằng', 4, 0.74], ['17', 'Tôn Nhật Minh', 4, 0.58],
  ['18', 'Nguyễn Hoàng Long', 3, 0.68], ['19', 'Trần Mỹ Duyên', 3, 0.80], ['20', 'Lê Công Thành', 3, 0.57],
  ['21', 'Phạm Bích Ngọc', 3, 0.74], ['22', 'Huỳnh Tấn Phát', 3, 0.50], ['23', 'Võ Thu Trang', 3, 0.90],
  ['24', 'Đoàn Khôi Nguyên', 2, 0.82],
].map(([n, name, jlpt, ability]) => ({
  id: `tt-hv-${n}`, name, role: 'STUDENT', jlpt, ability,
  levelId: { 5: LV.N5, 4: LV.N4, 3: LV.N3, 2: LV.N2 }[jlpt],
  createdDays: Math.round(between(35, 90)),
}));
const S = Object.fromEntries(STUDENTS.map((s) => [s.id.slice(-2), s]));
S['08'].createdDays = 2;          // vừa đăng ký, chưa làm gì — trang chủ trống cũng phải trông ổn
S['07'].google = true;            // đăng nhập bằng Google, không có mật khẩu
S['17'].locked = { daysAgo: 2, reason: 'Chia sẻ tài khoản cho nhiều người dùng chung trong phòng thi' };

const ALL_USERS = [ADMIN, ...TEACHERS, ...STUDENTS];

// ═══════════════════════════ Tag ═══════════════════════════

const TAG_NAMES = [
  'tro-tu-co-ban', 'loi-moi', 'the-te', 'tinh-tu-qua-khu', 'so-sanh', 'sap-xep-cau',
  'cach-doc-kanji', 'tu-vung-thoi-gian', 'tu-vung-dia-diem', 'tu-trai-nghia', 'dien-dat-lai',
  'the-y-chi', 'the-kha-nang', 'dieu-kien-tara', 'cho-nhan', 'the-sai-khien', 'truyen-dat-lai',
  'kinh-ngu', 'tu-lay', 'phong-doan', 'nguyen-nhan-ket-qua', 'nhuong-bo', 'doc-hieu-thong-bao',
  'doc-hieu-thu', 'doc-hieu-nghi-luan', 'tu-vung-cong-viec',
];
const TAG = Object.fromEntries(TAG_NAMES.map((t, i) => [t, 20001 + i]));

// ═══════════════════════════ Ngân hàng câu hỏi ═══════════════════════════

const BANKS = [
  { id: 20001, level: LV.N5, teacher: 'tt-gv-03', skill: 'VOCABULARY', title: 'N5 · 文字・語彙 — Chữ Hán và từ vựng' },
  { id: 20002, level: LV.N5, teacher: 'tt-gv-03', skill: 'GRAMMAR', title: 'N5 · 文法 — Ngữ pháp' },
  { id: 20003, level: LV.N5, teacher: 'tt-gv-03', skill: 'READING', title: 'N5 · 読解 — Đọc hiểu' },
  { id: 20004, level: LV.N4, teacher: 'tt-gv-02', skill: 'VOCABULARY', title: 'N4 · 文字・語彙 — Chữ Hán và từ vựng' },
  { id: 20005, level: LV.N4, teacher: 'tt-gv-02', skill: 'GRAMMAR', title: 'N4 · 文法 — Ngữ pháp' },
  { id: 20006, level: LV.N4, teacher: 'tt-gv-02', skill: 'READING', title: 'N4 · 読解 — Đọc hiểu' },
  { id: 20007, level: LV.N3, teacher: 'tt-gv-01', skill: 'VOCABULARY', title: 'N3 · 文字・語彙 — Chữ Hán và từ vựng' },
  { id: 20008, level: LV.N3, teacher: 'tt-gv-01', skill: 'GRAMMAR', title: 'N3 · 文法 — Ngữ pháp' },
  { id: 20009, level: LV.N3, teacher: 'tt-gv-01', skill: 'READING', title: 'N3 · 読解 — Đọc hiểu' },
  { id: 20010, level: LV.N2, teacher: 'tt-gv-01', skill: 'GRAMMAR', title: 'N2 · 文法・語彙 — Trọng tâm' },
];
const BANK = Object.fromEntries(BANKS.map((b) => [b.id, b]));

// Mỗi câu: [nội dung, [4 phương án], vị trí đáp án đúng, giải thích, độ khó 1–5, [tag], tuỳ chọn] tuỳ chọn.
const Q = {};

Q[20001] = [
  ['「わたしは まいあさ ７じに ＿＿＿。」', ['おきます', 'ねます', 'あそびます', 'すわります'], 0,
    'おきます (起きます) = thức dậy. Buổi sáng lúc 7 giờ thì hợp nghĩa nhất. ねます là đi ngủ.', 1, ['tu-vung-thoi-gian']],
  ['つぎの ことばの よみかたは どれですか。『山』', ['やま', 'かわ', 'き', 'た'], 0,
    '山 đọc là やま (núi). かわ là 川 (sông), き là 木 (cây), た là 田 (ruộng).', 1, ['cach-doc-kanji']],
  ['つぎの ことばの よみかたは どれですか。『水』', ['みず', 'ひ', 'き', 'つち'], 0,
    '水 = みず (nước). ひ là 火, き là 木, つち là 土 — bốn chữ này hay đi cùng nhau trong tên các ngày trong tuần.', 1, ['cach-doc-kanji']],
  ['「きのう デパートで くつを ＿＿＿。」', ['かいました', 'うりました', 'つくりました', 'とりました'], 0,
    'Ở cửa hàng bách hoá thì hành động tự nhiên là かいました (đã mua). うりました là đã bán — người mua không bán giày ở đó.', 2, []],
  ['「きょうは げつようびです。あしたは ＿＿＿です。」', ['かようび', 'すいようび', 'にちようび', 'どようび'], 0,
    'Thứ tự: げつ (thứ Hai) → か (thứ Ba) → すい (thứ Tư) → もく → きん → ど → にち.', 1, ['tu-vung-thoi-gian']],
  ['「えきの まえに ＿＿＿が あります。そこで こどもが あそんで います。」', ['こうえん', 'ぎんこう', 'びょういん', 'としょかん'], 0,
    'Trẻ con chơi đùa → こうえん (công viên). Ngân hàng, bệnh viện, thư viện đều không phải chỗ chơi.', 2, ['tu-vung-dia-diem']],
  ['つぎの ことばの よみかたは どれですか。『電車』', ['でんしゃ', 'でんき', 'じてんしゃ', 'でんわ'], 0,
    '電車 = でんしゃ (tàu điện). でんき là 電気 (điện), じてんしゃ là 自転車 (xe đạp), でんわ là 電話 (điện thoại).', 2, ['cach-doc-kanji']],
  ['「この へやは ＿＿＿です。なにも きこえません。」', ['しずか', 'にぎやか', 'きれい', 'ひろい'], 0,
    '"Không nghe thấy gì" → しずか (yên tĩnh). にぎやか là náo nhiệt, trái nghĩa hoàn toàn.', 2, ['tu-trai-nghia']],
  ['つぎの ことばの よみかたは どれですか。『先月』', ['せんげつ', 'さきげつ', 'せんつき', 'まえげつ'], 0,
    '先月 = せんげつ (tháng trước). Trong từ ghép Hán, 月 thường đọc げつ hoặc がつ: 今月 こんげつ, 一月 いちがつ.', 3, ['cach-doc-kanji', 'tu-vung-thoi-gian']],
  ['「あついですね。まどを ＿＿＿ください。」', ['あけて', 'しめて', 'けして', 'つけて'], 0,
    'Trời nóng thì mở cửa sổ → あけて. しめて là đóng. けして / つけて dùng cho đèn, điều hoà, không dùng cho cửa sổ.', 2, ['the-te', 'tu-trai-nghia']],
  ['「この かばんは かるくないです。」と だいたい おなじ いみの ぶんは どれですか。', ['この かばんは おもいです。', 'この かばんは ちいさいです。', 'この かばんは やすいです。', 'この かばんは あたらしいです。'], 0,
    'かるくない (không nhẹ) ≈ おもい (nặng). Dạng bài "diễn đạt lại" hỏi ý nghĩa, không hỏi từ giống hệt.', 3, ['dien-dat-lai', 'tu-trai-nghia']],
  ['つぎの ことばの よみかたは どれですか。『午後』', ['ごご', 'ごぜん', 'ごうご', 'ごあと'], 0,
    '午後 = ごご (buổi chiều, p.m.). 午前 = ごぜん (buổi sáng, a.m.).', 2, ['cach-doc-kanji', 'tu-vung-thoi-gian']],
];

Q[20002] = [
  ['「わたし ＿＿＿ ベトナムから きました。」', ['は', 'を', 'へ', 'で'], 0,
    'は đánh dấu chủ đề câu: "Tôi thì đến từ Việt Nam". を chỉ tân ngữ, không đi với 来ます theo nghĩa này.', 1, ['tro-tu-co-ban']],
  ['「としょかん ＿＿＿ ほんを よみます。」', ['で', 'に', 'を', 'が'], 0,
    'で chỉ nơi hành động diễn ra. Có động từ hành động (đọc sách) thì dùng で, không dùng に.', 1, ['tro-tu-co-ban']],
  ['「まいばん １１じ ＿＿＿ ねます。」', ['に', 'で', 'を', 'が'], 0,
    'Giờ cụ thể + に: 11じに ねます. Chú ý まいばん (mỗi tối) thì KHÔNG thêm に.', 1, ['tro-tu-co-ban', 'tu-vung-thoi-gian']],
  ['「あした いっしょに えいがを ＿＿＿か。」', ['みません', 'みました', 'みて', 'みたい'], 0,
    'Vませんか = lời mời lịch sự "…cùng … không?". あした (ngày mai) nên không thể là quá khứ みました.', 2, ['loi-moi']],
  ['「すみません、しおを ＿＿＿ください。」', ['とって', 'とる', 'とります', 'とった'], 0,
    'Nhờ vả: Vて ください. とる → とって.', 1, ['the-te']],
  ['「きのうは さむく ＿＿＿。」', ['なかったです', 'ないです', 'でした', 'かったです'], 0,
    'Tính từ い phủ định quá khứ: さむい → さむくなかったです. Không được nói さむいでした hay さむくでした.', 2, ['tinh-tu-qua-khu']],
  ['「この りんごは あの りんご ＿＿＿ おおきいです。」', ['より', 'ほど', 'まで', 'から'], 0,
    'A は B より + tính từ = A … hơn B. ほど chỉ dùng trong câu phủ định: A は B ほど おおきくない.', 2, ['so-sanh']],
  ['「にほんごの べんきょうは ＿＿＿ですか。」「おもしろいです。」', ['どう', 'どれ', 'どこ', 'だれ'], 0,
    'Hỏi cảm nhận, đánh giá → どうですか (thế nào?).', 1, []],
  ['「へやに だれ ＿＿＿ いません。」', ['も', 'が', 'か', 'を'], 0,
    'Từ để hỏi + も + phủ định = hoàn toàn không: だれも いません (không có ai cả).', 2, ['tro-tu-co-ban']],
  ['つぎの ぶんの ★ に 入る ものは どれですか。\nすみません、＿＿＿ ＿＿＿ ★ ＿＿＿ か。\n（トイレ ／ は ／ どこ ／ です）', ['どこ', 'は', 'です', 'トイレ'], 0,
    'Câu đúng: すみません、トイレ は どこ です か。Ô ★ là ô thứ ba → どこ.', 2, ['sap-xep-cau'], { type: 'SENTENCE_ORDERING' }],
  ['つぎの ぶんの ★ に 入る ものは どれですか。\nきのう ＿＿＿ ＿＿＿ ★ ＿＿＿ ました。\n（こうえん ／ で ／ ともだちと ／ あそび）', ['ともだちと', 'こうえん', 'で', 'あそび'], 0,
    'Câu đúng: きのう こうえん で ともだちと あそび ました。Nơi chốn + で đứng trước, động từ ghép với ました ở cuối.', 3, ['sap-xep-cau', 'tro-tu-co-ban'], { type: 'SENTENCE_ORDERING' }],
  ['「わたしは ねこが ＿＿＿です。」', ['すき', 'すきな', 'すきに', 'すきで'], 0,
    'Tính từ な đứng cuối câu trước です thì bỏ な: すきです. すきな chỉ dùng khi đứng trước danh từ: すきな たべもの.', 1, []],
];

const P = {}; // bài đọc theo ngân hàng
P[20003] = [
  { title: 'メモ（会社で）', content:
    '田中さん\n\nきょうは 3時から 会議が あります。会議の 前に、2かいの コピーきで しりょうを 10まい コピーして ください。わたしは 2時半ごろ 会社に もどります。\n\n山田',
    questions: [
      ['田中さんは 3時までに 何を しますか。', ['しりょうを コピーします', '会議に でます', '山田さんに でんわします', '2かいに しりょうを もって いきます'], 0,
        'Mẩu nhắn yêu cầu "trước cuộc họp (3 giờ), hãy photo 10 tờ tài liệu". Việc phải làm trước 3 giờ là photo.', 2, ['doc-hieu-thong-bao']],
      ['山田さんは 何時ごろ 会社に もどりますか。', ['2時半ごろ', '3時', '2時', '3時半ごろ'], 0,
        '「2時半ごろ 会社に もどります」— khoảng 2 giờ rưỡi. 3 giờ là giờ họp, dễ nhầm.', 1, ['doc-hieu-thong-bao']],
    ] },
  { title: 'わたしの 一日', content:
    'わたしは グエンです。ベトナムの ハノイから 来ました。いま、東京の 日本語学校で べんきょうして います。\n\nまいあさ 7時に おきて、電車で 学校へ 行きます。学校は 9時から 1時までです。ごごは スーパーで アルバイトを します。しゅうまつは ともだちと こうえんで サッカーを します。',
    questions: [
      ['グエンさんは ごご 何を しますか。', ['アルバイトを します', '日本語を べんきょうします', 'サッカーを します', '学校へ 行きます'], 0,
        '「ごごは スーパーで アルバイトを します」. Đá bóng là việc cuối tuần, học là buổi sáng.', 1, []],
      ['ただしい ものは どれですか。', ['グエンさんは 電車で 学校へ 行きます', '学校は 1時からです', 'しゅうまつに アルバイトを します', 'グエンさんは 東京から 来ました'], 0,
        'Bài viết 「電車で 学校へ 行きます」. Trường học từ 9 giờ đến 1 giờ, làm thêm buổi chiều, và Nguyễn đến từ Hà Nội.', 2, []],
      ['学校は 何時間 ありますか。', ['4時間', '3時間', '5時間', '9時間'], 0,
        'Từ 9 giờ đến 1 giờ chiều là 4 tiếng. Dạng câu tính toán từ hai mốc giờ rất hay gặp ở N5.', 2, []],
    ] },
];

Q[20004] = [
  ['つぎの ことばの 読み方は どれですか。『急ぐ』', ['いそぐ', 'きゅうぐ', 'おそぐ', 'はやぐ'], 0,
    '急ぐ = いそぐ (vội). Âm Hán của 急 là キュウ: 急行 きゅうこう (tàu nhanh).', 2, ['cach-doc-kanji']],
  ['つぎの ことばの 読み方は どれですか。『説明』', ['せつめい', 'せいめい', 'せつみょう', 'しょうめい'], 0,
    '説明 = せつめい (giải thích). 明 có hai âm メイ và ミョウ — trong 説明 đọc メイ.', 3, ['cach-doc-kanji']],
  ['「ねつが あるので、きょうは 会社を ＿＿＿。」', ['休みます', 'やめます', 'おくれます', 'わすれます'], 0,
    'Bị sốt → nghỉ làm hôm nay: 会社を 休みます. 会社を やめます là nghỉ việc hẳn — quá mức với một cơn sốt.', 2, ['tu-vung-cong-viec']],
  ['「この しりょうを 2まい ＿＿＿して ください。」', ['コピー', 'メモ', 'チェック', 'サイン'], 0,
    '"2 tờ" + tài liệu → photo 2 bản. Các từ còn lại không đi với số tờ theo nghĩa này.', 2, ['tu-vung-cong-viec']],
  ['「家を 出るのが おそく なって、電車に ＿＿＿。」', ['間に合いませんでした', '乗りかえました', 'おりました', 'ひろいました'], 0,
    'Ra khỏi nhà muộn → không kịp tàu: 電車に 間に合わない. Đây là cặp từ hay ra: 間に合う (kịp) ↔ 遅れる (trễ).', 3, ['tu-vung-thoi-gian']],
  ['つぎの ことばの 読み方は どれですか。『約束』', ['やくそく', 'やくぞく', 'よくそく', 'やくそ'], 0,
    '約束 = やくそく (hẹn, lời hứa). Không biến âm thành ぞく.', 2, ['cach-doc-kanji']],
  ['「あの 店は いつも 人が 多くて ＿＿＿です。」', ['にぎやか', 'ていねい', 'ふべん', 'しんせつ'], 0,
    'Đông người → にぎやか (náo nhiệt). ていねい (lịch sự) hay しんせつ (tốt bụng) nói về cách phục vụ, không liên quan việc đông.', 2, []],
  ['「母は 料理が じょうずです。」と だいたい 同じ 意味の 文は どれですか。', ['母は 料理を するのが うまいです。', '母は 料理が 好きです。', '母は よく 料理を します。', '母は 料理を 習って います。'], 0,
    'じょうず ≈ うまい (giỏi). "Thích nấu" hay "hay nấu" không có nghĩa là nấu giỏi.', 3, ['dien-dat-lai']],
  ['つぎの ことばの 読み方は どれですか。『準備』', ['じゅんび', 'じゅんぴ', 'しゅんび', 'じゅうび'], 0,
    '準備 = じゅんび (chuẩn bị).', 3, ['cach-doc-kanji']],
  ['「さいふを ＿＿＿ しまいました。交番に 行きます。」', ['落として', '拾って', '渡して', '返して'], 0,
    'Đi đồn cảnh sát (交番) sau khi làm gì với ví → đánh rơi: 落として しまいました. てしまう diễn tả việc lỡ xảy ra, đáng tiếc.', 2, ['the-te']],
];

Q[20005] = [
  ['「日本へ 行ったら、ふじさんに ＿＿＿と 思って います。」', ['登ろう', '登れ', '登って', '登った'], 0,
    'Dự định: Vよう（thể ý chí）と 思って います. 登る → 登ろう. 登れ là thể mệnh lệnh.', 2, ['the-y-chi']],
  ['「すみません、この 漢字、＿＿＿か。わたしは 読めないんです。」', ['読めます', '読ませます', '読んで います', '読みましょう'], 0,
    'Hỏi người khác có ĐỌC ĐƯỢC không → thể khả năng 読めます. 読ませます là bắt/cho ai đọc.', 2, ['the-kha-nang']],
  ['「雨が ＿＿＿、しあいは 中止です。」', ['降ったら', '降っても', '降るのに', '降りながら'], 0,
    'Điều kiện "nếu mưa thì trận đấu huỷ" → たら. 降っても nghĩa ngược lại (dù mưa vẫn…).', 2, ['dieu-kien-tara']],
  ['「先生に しゅくだいを ＿＿＿。」', ['見て いただきました', '見て くださいました', '見て あげました', '見て くれました'], 0,
    'Người nhận ơn là mình, người làm là thầy (được đánh dấu bằng に) → Vて いただきました. くださる / くれる phải dùng が: 先生が 見て くださいました.', 3, ['cho-nhan', 'kinh-ngu']],
  ['「子どもの ころ、きらいな 野菜を 母に ＿＿＿。」', ['食べさせられました', '食べられました', '食べさせて くれました', '食べました'], 0,
    'Bị bắt phải làm việc mình không muốn → thể bị động sai khiến 食べさせられる. Chữ "きらいな" là manh mối.', 4, ['the-sai-khien']],
  ['「この 本は 字が 大きいので、読み ＿＿＿です。」', ['やすい', 'にくい', 'たい', 'すぎ'], 0,
    'Chữ to → dễ đọc: V(bỏ ます)＋やすい. にくい là khó.', 2, []],
  ['「あしたは 雨が 降る ＿＿＿です。天気予報で 言って いました。」', ['そう', 'つもり', 'ため', 'ところ'], 0,
    'Truyền đạt lại thông tin nghe được: thể thường ＋ そうです. Khác với 降りそうです (trông như sắp mưa).', 3, ['truyen-dat-lai']],
  ['「ドアが ＿＿＿ います。だれか 開けたんでしょう。」', ['開いて', '開けて', '開いた', '開く'], 0,
    'Trạng thái còn lại của sự việc, tự động từ ＋ て います: ドアが 開いて います. Tha động từ 開ける đi với を.', 3, ['the-te']],
  ['「この かばんは ＿＿＿、買えません。」', ['高すぎて', '高すぎに', '高すぎな', '高すぎる'], 0,
    'Chỉ nguyên nhân rồi nối vế sau → thể て: 高すぎて 買えません (đắt quá nên không mua được).', 2, ['nguyen-nhan-ket-qua']],
  ['つぎの 文の ★ に 入る ものは どれですか。\nこの 仕事は ＿＿＿ ＿＿＿ ★ ＿＿＿ ください。\n（あした ／ までに ／ 必ず ／ 終わらせて）', ['必ず', 'あした', 'までに', '終わらせて'], 0,
    'Câu đúng: この仕事は あした までに 必ず 終わらせて ください。Phó từ 必ず đứng ngay trước động từ.', 3, ['sap-xep-cau'], { type: 'SENTENCE_ORDERING' }],
  ['「部長は もう お帰りに ＿＿＿。」', ['なりました', 'しました', 'いたしました', 'まいりました'], 0,
    'Tôn kính ngữ: お＋V(bỏ ます)＋に なる. いたす / まいる là khiêm nhường ngữ, không dùng cho cấp trên.', 3, ['kinh-ngu']],
  ['「重そうですね。わたしが お荷物を ＿＿＿。」', ['お持ちします', 'お持ちに なります', '持たれます', 'お持ち ください'], 0,
    'Mình làm cho người khác → khiêm nhường ngữ お＋V(bỏ ます)＋する: お持ちします.', 3, ['kinh-ngu']],
];

P[20006] = [
  { title: 'メール：パーティーの 場所', content:
    'リンさん\n\n来週の 金曜日の パーティーの ことですが、場所が かわりました。駅前の レストランは 予約が いっぱいだったので、大学の 近くの 「さくら」という 店に しました。時間は 同じで、7時からです。\n\n「さくら」は 駅から 少し 遠いので、6時半に 駅の 北口で 待ち合わせを しませんか。\n\n木村',
    questions: [
      ['パーティーの 場所は どうして かわりましたか。', ['駅前の レストランの 予約が とれなかったから', '「さくら」の ほうが 安いから', '駅前の レストランが 遠いから', '時間が かわったから'], 0,
        '「予約が いっぱいだったので」→ không đặt được chỗ. Thời gian 「同じ」 nên không đổi.', 2, ['doc-hieu-thu', 'nguyen-nhan-ket-qua']],
      ['リンさんは 金曜日の 6時半に どこへ 行けば いいですか。', ['駅の 北口', '「さくら」', '大学', '駅前の レストラン'], 0,
        'Kimura rủ 「6時半に 駅の 北口で 待ち合わせ」. 7 giờ mới là giờ bắt đầu ở quán.', 2, ['doc-hieu-thu']],
    ] },
  { title: '図書館からの お知らせ', content:
    '【図書館からの お知らせ】\n\n・本は 2週間 借りる ことが できます。\n・一人 10さつまで 借りられます。\n・借りた 本を 返すのが おくれた 人は、返した 日から 1週間、本を 借りる ことが できません。\n・雑誌は 図書館の 中で 読んで ください。借りる ことは できません。',
    questions: [
      ['この お知らせの 内容と 合う ものは どれですか。', ['雑誌は 家で 読む ことが できない', '本は 1か月 借りられる', '一人 何さつでも 借りられる', '返すのが おくれても すぐ また 借りられる'], 0,
        'Tạp chí chỉ đọc tại thư viện, không cho mượn về. Sách mượn 2 tuần, tối đa 10 cuốn.', 2, ['doc-hieu-thong-bao']],
      ['3日 おくれて 本を 返した 人は、いつから また 本を 借りられますか。', ['返した 日の 1週間 後から', '返した 次の 日から', '3日 後から', '2週間 後から'], 0,
        'Trả trễ thì "từ ngày trả, trong 1 tuần không được mượn" — không phụ thuộc trễ bao nhiêu ngày.', 3, ['doc-hieu-thong-bao']],
    ] },
];

Q[20007] = [
  ['次の 言葉の 読み方として 最も よい ものは どれか。『招待』', ['しょうたい', 'しょうだい', 'そうたい', 'しょたい'], 0,
    '招待 = しょうたい (mời). Hay nhầm với 正体 しょうたい (bộ mặt thật) — cùng cách đọc, khác chữ.', 3, ['cach-doc-kanji']],
  ['次の 言葉の 読み方として 最も よい ものは どれか。『効果』', ['こうか', 'こうけ', 'きょうか', 'こうが'], 0,
    '効果 = こうか (hiệu quả). 果 đọc カ: 結果 けっか.', 3, ['cach-doc-kanji']],
  ['「新しい 仕事に まだ ＿＿＿ いないので、毎日 疲れる。」', ['慣れて', '困って', '迷って', '飽きて'], 0,
    'Việc mới, chưa QUEN nên mệt → 慣れて いない. 飽きる là chán — không hợp với "việc mới".', 3, ['tu-vung-cong-viec']],
  ['「約束の 時間に ＿＿＿ しまい、友だちを 30分も 待たせた。」', ['遅れて', '間に合って', '急いで', '過ぎて'], 0,
    'Để bạn đợi 30 phút → đến trễ: 遅れて しまい.', 3, ['nguyen-nhan-ket-qua']],
  ['「部屋を ＿＿＿ 片付けたら、気持ちが よく なった。」', ['すっかり', 'うっかり', 'がっかり', 'のんびり'], 0,
    'すっかり = hoàn toàn, sạch sẽ. うっかり là lơ đễnh, がっかり là thất vọng, のんびり là thong thả.', 4, ['tu-lay']],
  ['「会議は 延期に なった。」と 意味が 最も 近い ものは どれか。', ['会議は あとの 日に 行う ことに なった', '会議は 中止に なった', '会議は 早く 始まる ことに なった', '会議は 場所が 変わった'], 0,
    '延期 = hoãn sang ngày khác. 中止 là huỷ hẳn — hai từ này rất hay bị đặt cạnh nhau để gài bẫy.', 4, ['dien-dat-lai']],
  ['次の 言葉の 読み方として 最も よい ものは どれか。『乗り換える』', ['のりかえる', 'のりかわる', 'じょうかえる', 'のりがえる'], 0,
    '乗り換える = のりかえる (đổi tàu). 乗り換え (danh từ) cũng đọc のりかえ, không biến âm.', 3, ['cach-doc-kanji']],
  ['「弟は ＿＿＿ 性格で、だれとでも すぐ 友だちに なる。」', ['明るい', '暗い', '細かい', '厳しい'], 0,
    'Kết bạn nhanh với bất kỳ ai → tính cách 明るい (vui vẻ, cởi mở).', 3, []],
];

Q[20008] = [
  ['「日本に 来た ＿＿＿、日本語が ぜんぜん わからなかった。」', ['ばかりの ころは', 'ところで', 'うちに', 'あいだに'], 0,
    'Vた ばかり = vừa mới. 来たばかりの ころは = hồi mới đến. うちに / あいだに không đi với thể た theo nghĩa này.', 3, []],
  ['「雨が 降り ＿＿＿だから、かさを 持って 行きなさい。」', ['そう', 'よう', 'らしい', 'みたい'], 0,
    'Chỉ そう mới gắn vào gốc động từ bỏ ます: 降りそう (trông như sắp mưa). よう / らしい / みたい đi với thể thường: 降るようだ.', 3, ['phong-doan']],
  ['「この レストランは 値段が 安い ＿＿＿、とても おいしい。」', ['わりに', 'くせに', 'ために', 'ように'], 0,
    'わりに = so với … thì (hơn mong đợi). くせに mang ý chê trách, không hợp với lời khen.', 4, ['nhuong-bo']],
  ['「子どもの ＿＿＿、よく この 川で 泳いだ ものだ。」', ['ころ', 'うち', 'まで', 'あいだ'], 0,
    '〜の ころ ＋ たものだ = hồi còn … thường hay … (hoài niệm).', 3, []],
  ['「急いで いた ＿＿＿、財布を 家に 忘れて しまった。」', ['せいで', 'おかげで', 'ように', 'ことに'], 0,
    'Nguyên nhân dẫn tới kết quả xấu → せいで. おかげで dùng cho kết quả tốt.', 3, ['nguyen-nhan-ket-qua']],
  ['「天気予報 ＿＿＿、午後から 雪が 降るそうだ。」', ['によると', 'について', 'にとって', 'によって'], 0,
    'Nguồn tin + によると ＋ 〜そうだ (theo … thì nghe nói). について là "về", にとって là "đối với".', 3, ['truyen-dat-lai']],
  ['「この 仕事は 私 ＿＿＿ 難しすぎる。」', ['にとって', 'について', 'によって', 'として'], 0,
    'Đánh giá từ góc nhìn của ai → にとって (đối với tôi).', 3, []],
  ['「どんなに 練習 ＿＿＿、上手に ならない。」', ['しても', 'したら', 'すれば', 'するなら'], 0,
    'どんなに 〜ても = dù … đến mấy cũng. どんなに luôn đi với ても.', 3, ['nhuong-bo']],
  ['次の 文の ★ に 入る 最も よい ものは どれか。\n彼女は ＿＿＿ ＿＿＿ ★ ＿＿＿ いる。\n（医者に ／ なる ／ ために ／ 勉強して）', ['ために', '医者に', 'なる', '勉強して'], 0,
    'Câu đúng: 彼女は 医者に なる ために 勉強して いる。Vる ＋ ために = để (mục đích).', 4, ['sap-xep-cau'], { type: 'SENTENCE_ORDERING' }],
  ['「担当の 者が 参りますので、こちらで 少々 ＿＿＿ ください。」', ['お待ち', 'お待ちして', '待たれて', '待ちして'], 0,
    'Mời khách: お＋V(bỏ ます)＋ください → お待ち ください.', 3, ['kinh-ngu']],
];

P[20009] = [
  { title: '歩きスマホ', content:
    '最近、駅や 電車の 中で スマートフォンを 見ながら 歩く 人が 増えている。画面に 集中して いると、周りの 様子に 気が つかず、人に ぶつかったり、階段で 転んだり する ことが ある。\n\nある 鉄道会社の 調査では、駅で 起きた けがの うち、約 3割が 歩きスマホに 関係して いたそうだ。便利な 道具だからこそ、使う 場所と 時間を 考える ことが 大切だ。',
    questions: [
      ['この 文章で 筆者が 一番 言いたい ことは 何か。', ['スマートフォンは 場所と 時間を 考えて 使うべきだ', 'スマートフォンは 危ないので 使わない ほうが いい', '駅の けがは すべて 歩きスマホが 原因だ', '鉄道会社は 調査を もっと するべきだ'], 0,
        'Ý chính nằm ở câu cuối 「使う場所と時間を考えることが大切だ」. Bài không khuyên bỏ hẳn điện thoại.', 3, ['doc-hieu-nghi-luan']],
      ['調査で わかった ことは 何か。', ['駅での けがの 約 3割が 歩きスマホに 関係していた', '駅を 利用する 人の 3割が 歩きスマホを している', '歩きスマホを する 人が 3割 減った', '階段での けがが 一番 多い'], 0,
        '3割 là tỉ lệ trong số VỤ BỊ THƯƠNG, không phải tỉ lệ người đi đường. Đáp án thứ hai là bẫy đổi chủ thể.', 3, ['doc-hieu-nghi-luan']],
    ] },
  { title: '朝の ジョギング', content:
    '私は 毎朝、会社へ 行く 前に 30分 ジョギングを して いる。始めた ころは 早く 起きるのが つらくて、何度も やめようと 思った。\n\nしかし、1か月 ほど 続けると、体が 軽く なり、仕事中も 眠く ならなく なった。今では、走らない 日の ほうが 調子が 悪いくらいだ。何かを 習慣に するには、最初の 1か月を がんばる ことが 大切だと 思う。',
    questions: [
      ['筆者が ジョギングを 続けて 変わった ことは 何か。', ['仕事中に 眠く ならなく なった', '早く 起きるのが つらく なった', '走らない 日が 増えた', '会社へ 行く 時間が 遅く なった'], 0,
        '「仕事中も 眠く ならなく なった」. Việc dậy sớm thấy khổ là chuyện lúc MỚI bắt đầu.', 3, ['doc-hieu-nghi-luan']],
      ['筆者の 考えに 合う ものは どれか。', ['習慣に するには 最初の 1か月が 大切だ', '毎朝 30分 走るのは 体に 悪い', '何度も やめようと 思うなら やめた ほうが いい', '走らない 日を 作る ことが 大切だ'], 0,
        'Câu kết nêu quan điểm: tạo thói quen thì cần cố gắng tháng đầu tiên.', 3, ['doc-hieu-nghi-luan']],
    ] },
];

Q[20010] = [
  ['「雨 ＿＿＿、試合は 予定通り 行われた。」', ['にもかかわらず', 'にしたがって', 'に比べて', 'をきっかけに'], 0,
    'にもかかわらず = mặc dù (kết quả trái với dự đoán). Mưa nhưng vẫn thi đấu đúng lịch.', 4, ['nhuong-bo']],
  ['「毎日 努力した ＿＿＿、第一志望の 大学に 合格できた。」', ['かいがあって', 'あげく', 'ものの', 'どころか'], 0,
    'かいがあって = công sức không uổng, nhờ đó mà có kết quả tốt. あげく dùng cho kết cục xấu sau thời gian dài.', 4, ['nguyen-nhan-ket-qua']],
  ['「この 仕事は 年齢や 経験 ＿＿＿、誰でも 応募できる。」', ['を問わず', 'にこたえて', 'に沿って', 'をめぐって'], 0,
    'を問わず = không kể, bất kể. Hay đi với các cặp từ đối lập: 年齢を問わず, 男女を問わず.', 4, []],
  ['次の 言葉の 読み方として 最も よい ものは どれか。『把握』', ['はあく', 'はおく', 'ばあく', 'はやく'], 0,
    '把握 = はあく (nắm bắt). Từ văn viết rất hay gặp trong đề đọc hiểu N2.', 4, ['cach-doc-kanji'], { skill: 'VOCABULARY' }],
  ['次の 言葉の 読み方として 最も よい ものは どれか。『妥協』', ['だきょう', 'だっきょう', 'たきょう', 'だこう'], 0,
    '妥協 = だきょう (thoả hiệp). Không có âm ngắt っ.', 5, ['cach-doc-kanji'], { skill: 'VOCABULARY' }],
  ['「全員が 参加できるように、会議の 日程を ＿＿＿した。」', ['調整', '調節', '調査', '調和'], 0,
    '調整 = điều chỉnh cho các bên khớp nhau (lịch, ý kiến). 調節 dùng cho mức độ vật lý: 温度を調節する.', 5, ['tu-vung-cong-viec'], { skill: 'VOCABULARY' }],
];

// ─── Đánh số câu hỏi / đáp án ───
const QUESTIONS = [];
const PASSAGES = [];
let nextQ = 20001; let nextA = 100001; let nextPassage = 20001;
function addQuestion(bankId, spec, passage = null) {
  const [content, options, correct, explanation, difficulty, tags, extra = {}] = spec;
  const bank = BANK[bankId];
  const q = {
    id: nextQ++, bankId, levelId: bank.level, content, explanation, difficulty,
    type: extra.type ?? 'MULTIPLE_CHOICE', skill: extra.skill ?? bank.skill,
    tags, passage, createdDays: bank.level === LV.N5 ? 95 : bank.level === LV.N4 ? 92 : 88,
    answers: options.map((text, i) => ({ id: nextA++, text, correct: i === correct })),
  };
  // Xáo vị trí đáp án đúng khi lưu vào ngân hàng.
  const shift = Math.floor(rand() * q.answers.length);
  q.answers = q.answers.slice(shift).concat(q.answers.slice(0, shift));
  QUESTIONS.push(q);
  return q;
}
for (const bank of BANKS) {
  for (const spec of Q[bank.id] ?? []) addQuestion(bank.id, spec);
  for (const p of P[bank.id] ?? []) {
    const passage = { id: nextPassage++, bankId: bank.id, title: p.title, content: p.content };
    PASSAGES.push(passage);
    for (const spec of p.questions) addQuestion(bank.id, spec, passage);
  }
}
const questionsOf = (bankId) => QUESTIONS.filter((q) => q.bankId === bankId);

// ═══════════════════════════ Từ vựng, chữ Hán, bộ thẻ ═══════════════════════════

// Từ vựng/chữ Hán N5 đã có trong DB (v1.4.0). Hai bảng có UNIQUE — trùng là hỏng cả file.
const EXISTING_VOCAB = new Set('今日|きょう 休む|やすむ 会社|かいしゃ 便利|べんり 働く|はたらく 元気|げんき 先生|せんせい 友達|ともだち 古い|ふるい 大きい|おおきい 天気|てんき 好き|すき 学校|がっこう 学生|がくせい 安い|やすい 家族|かぞく 寝る|ねる 小さい|ちいさい 帰る|かえる 新しい|あたらしい 明日|あした 易しい|やさしい 時間|じかん 書く|かく 来る|くる 毎日|まいにち 病院|びょういん 聞く|きく 行く|いく 見る|みる 話す|はなす 読む|よむ 買う|かう 起きる|おきる 難しい|むずかしい 電車|でんしゃ 静か|しずか 食べる|たべる 飲む|のむ 高い|たかい'.split(' '));
const EXISTING_KANJI = new Set([...'上下中人先土大学小山川年日時月木水火生金']);

const VOCAB_N4 = [
  ['急ぐ', 'いそぐ', 'vội, gấp', 'động từ', '時間が ないので 急いで ください。', 'Không có thời gian nên hãy nhanh lên.'],
  ['説明', 'せつめい', 'giải thích', 'danh từ', '先生の 説明は わかりやすいです。', 'Lời giải thích của thầy dễ hiểu.'],
  ['準備', 'じゅんび', 'chuẩn bị', 'danh từ', '旅行の 準備を して います。', 'Tôi đang chuẩn bị cho chuyến du lịch.'],
  ['約束', 'やくそく', 'lời hứa, cuộc hẹn', 'danh từ', '友だちと 3時に 会う 約束が あります。', 'Tôi có hẹn gặp bạn lúc 3 giờ.'],
  ['予約', 'よやく', 'đặt trước', 'danh từ', 'レストランを 予約しました。', 'Tôi đã đặt bàn ở nhà hàng.'],
  ['経験', 'けいけん', 'kinh nghiệm', 'danh từ', '日本で 働いた 経験が あります。', 'Tôi có kinh nghiệm làm việc ở Nhật.'],
  ['会議', 'かいぎ', 'cuộc họp', 'danh từ', '会議は 10時から です。', 'Cuộc họp bắt đầu từ 10 giờ.'],
  ['資料', 'しりょう', 'tài liệu', 'danh từ', '会議の 資料を コピーします。', 'Tôi photo tài liệu cuộc họp.'],
  ['遅れる', 'おくれる', 'trễ, muộn', 'động từ', '電車が 遅れて います。', 'Tàu điện đang bị trễ.'],
  ['間に合う', 'まにあう', 'kịp', 'động từ', 'タクシーで 行けば 間に合います。', 'Đi taxi thì sẽ kịp.'],
  ['落とす', 'おとす', 'đánh rơi', 'động từ', 'かぎを 落として しまいました。', 'Tôi lỡ đánh rơi chìa khoá.'],
  ['拾う', 'ひろう', 'nhặt', 'động từ', '道で さいふを 拾いました。', 'Tôi nhặt được ví trên đường.'],
  ['届ける', 'とどける', 'giao đến, mang đến', 'động từ', '荷物を 家まで 届けます。', 'Chúng tôi giao hàng tận nhà.'],
  ['集める', 'あつめる', 'thu thập, sưu tầm', 'động từ', '古い 切手を 集めて います。', 'Tôi sưu tầm tem cũ.'],
  ['決める', 'きめる', 'quyết định', 'động từ', '旅行の 日を 決めましょう。', 'Hãy quyết định ngày đi du lịch.'],
  ['続ける', 'つづける', 'tiếp tục', 'động từ', '毎日 日本語の 勉強を 続けて います。', 'Ngày nào tôi cũng tiếp tục học tiếng Nhật.'],
  ['片付ける', 'かたづける', 'dọn dẹp', 'động từ', '使った ものは 片付けて ください。', 'Đồ đã dùng thì hãy dọn lại.'],
  ['引っ越す', 'ひっこす', 'chuyển nhà', 'động từ', '来月 大阪に 引っ越します。', 'Tháng sau tôi chuyển nhà đến Osaka.'],
  ['楽しみ', 'たのしみ', 'niềm vui, mong chờ', 'danh từ', '夏休みが 楽しみです。', 'Tôi rất mong kỳ nghỉ hè.'],
  ['趣味', 'しゅみ', 'sở thích', 'danh từ', '私の 趣味は 写真を とる ことです。', 'Sở thích của tôi là chụp ảnh.'],
  ['丁寧', 'ていねい', 'lịch sự, cẩn thận', 'tính từ な', '店員が 丁寧に 説明して くれました。', 'Nhân viên đã giải thích rất lịch sự.'],
  ['親切', 'しんせつ', 'tốt bụng', 'tính từ な', '隣の 人は とても 親切です。', 'Người hàng xóm rất tốt bụng.'],
  ['複雑', 'ふくざつ', 'phức tạp', 'tính từ な', 'この 問題は 複雑です。', 'Vấn đề này phức tạp.'],
  ['簡単', 'かんたん', 'đơn giản', 'tính từ な', 'この 料理は 作り方が 簡単です。', 'Món này cách làm đơn giản.'],
  ['必要', 'ひつよう', 'cần thiết', 'tính từ な', 'パスポートが 必要です。', 'Cần có hộ chiếu.'],
  ['特に', 'とくに', 'đặc biệt là', 'phó từ', '日本料理の 中で 特に すしが 好きです。', 'Trong các món Nhật, tôi đặc biệt thích sushi.'],
  ['必ず', 'かならず', 'nhất định, chắc chắn', 'phó từ', '明日は 必ず 来て ください。', 'Ngày mai nhất định hãy đến nhé.'],
  ['最近', 'さいきん', 'gần đây', 'danh từ', '最近 寒く なりましたね。', 'Gần đây trời lạnh hẳn rồi nhỉ.'],
  ['途中', 'とちゅう', 'giữa chừng, dọc đường', 'danh từ', '会社へ 行く 途中で 銀行に 寄ります。', 'Trên đường đi làm tôi ghé ngân hàng.'],
  ['都合', 'つごう', 'sự thuận tiện (thời gian)', 'danh từ', '明日の 都合は どうですか。', 'Ngày mai anh có tiện không?'],
];
const VOCAB_N3 = [
  ['招待', 'しょうたい', 'mời', 'danh từ', '結婚式に 招待されました。', 'Tôi được mời dự đám cưới.'],
  ['効果', 'こうか', 'hiệu quả', 'danh từ', 'この 薬は すぐに 効果が 出ます。', 'Thuốc này có hiệu quả ngay.'],
  ['延期', 'えんき', 'hoãn lại', 'danh từ', '雨で 試合が 延期に なった。', 'Trận đấu bị hoãn vì mưa.'],
  ['中止', 'ちゅうし', 'huỷ bỏ', 'danh từ', '台風で イベントは 中止だ。', 'Sự kiện bị huỷ vì bão.'],
  ['慣れる', 'なれる', 'quen với', 'động từ', '日本の 生活に もう 慣れました。', 'Tôi đã quen với cuộc sống ở Nhật.'],
  ['迷う', 'まよう', 'lạc đường, phân vân', 'động từ', 'どちらに するか 迷って いる。', 'Tôi đang phân vân chọn cái nào.'],
  ['増える', 'ふえる', 'tăng lên', 'động từ', '外国人の 観光客が 増えて いる。', 'Khách du lịch nước ngoài đang tăng.'],
  ['減る', 'へる', 'giảm đi', 'động từ', '最近 体重が 減った。', 'Gần đây cân nặng của tôi giảm.'],
  ['調査', 'ちょうさ', 'điều tra, khảo sát', 'danh từ', '若者の 生活について 調査した。', 'Đã khảo sát về đời sống của giới trẻ.'],
  ['関係', 'かんけい', 'quan hệ, liên quan', 'danh từ', 'その 話は 私には 関係ない。', 'Chuyện đó không liên quan đến tôi.'],
  ['習慣', 'しゅうかん', 'thói quen, tập quán', 'danh từ', '寝る 前に 本を 読む 習慣が ある。', 'Tôi có thói quen đọc sách trước khi ngủ.'],
  ['集中', 'しゅうちゅう', 'tập trung', 'danh từ', 'うるさくて 集中できない。', 'Ồn quá không tập trung được.'],
  ['周り', 'まわり', 'xung quanh', 'danh từ', '周りの 人に 迷惑を かけないで。', 'Đừng làm phiền người xung quanh.'],
  ['性格', 'せいかく', 'tính cách', 'danh từ', '姉は 明るい 性格だ。', 'Chị gái tôi có tính cách vui vẻ.'],
  ['乗り換える', 'のりかえる', 'đổi tàu, chuyển tuyến', 'động từ', '次の 駅で 地下鉄に 乗り換える。', 'Đổi sang tàu điện ngầm ở ga tới.'],
];
const VOCAB_N5 = [
  ['午後', 'ごご', 'buổi chiều', 'danh từ', '午後 3時に 会いましょう。', 'Hãy gặp nhau lúc 3 giờ chiều.'],
  ['午前', 'ごぜん', 'buổi sáng', 'danh từ', '午前中は 家に います。', 'Cả buổi sáng tôi ở nhà.'],
  ['先月', 'せんげつ', 'tháng trước', 'danh từ', '先月 日本へ 行きました。', 'Tháng trước tôi đã đi Nhật.'],
  ['銀行', 'ぎんこう', 'ngân hàng', 'danh từ', '銀行は 3時まで です。', 'Ngân hàng làm việc đến 3 giờ.'],
  ['公園', 'こうえん', 'công viên', 'danh từ', '公園で 子どもが 遊んで います。', 'Trẻ con đang chơi ở công viên.'],
  ['図書館', 'としょかん', 'thư viện', 'danh từ', '図書館で 勉強します。', 'Tôi học ở thư viện.'],
  ['開ける', 'あける', 'mở', 'động từ', '窓を 開けて ください。', 'Hãy mở cửa sổ ra.'],
  ['閉める', 'しめる', 'đóng', 'động từ', 'ドアを 閉めて ください。', 'Hãy đóng cửa lại.'],
  ['軽い', 'かるい', 'nhẹ', 'tính từ い', 'この かばんは 軽いです。', 'Cái cặp này nhẹ.'],
  ['重い', 'おもい', 'nặng', 'tính từ い', '荷物が 重いです。', 'Hành lý nặng.'],
];

const KANJI_N4 = [
  ['会', 'カイ・エ', 'あ-う', 'HỘI · gặp gỡ, hội họp', 6, '人', 'Mái nhà che hai người đang gặp nhau.'],
  ['社', 'シャ', 'やしろ', 'XÃ · công ty, đền thờ', 7, '示', 'Bộ thị (thần) đứng cạnh đất — nơi thờ thần của làng.'],
  ['員', 'イン', '', 'VIÊN · thành viên', 10, '口', 'Cái miệng trên đồng tiền — người được trả lương.'],
  ['医', 'イ', '', 'Y · y tế, chữa bệnh', 7, '匚', 'Mũi tên nằm trong hộp — lấy mũi tên ra khỏi vết thương.'],
  ['者', 'シャ', 'もの', 'GIẢ · người', 8, '老', 'Xuất hiện ở cuối từ chỉ người: 医者, 学者.'],
  ['病', 'ビョウ・ヘイ', 'や-む、やまい', 'BỆNH · bệnh tật', 10, '疒', 'Bộ nạch (giường bệnh) bao lấy chữ bính.'],
  ['院', 'イン', '', 'VIỆN · viện, toà nhà lớn', 10, '阜', 'Có trong 病院 (bệnh viện), 大学院 (cao học).'],
  ['駅', 'エキ', '', 'DỊCH · nhà ga', 14, '馬', 'Bộ mã (ngựa) — ngày xưa là trạm đổi ngựa.'],
  ['室', 'シツ', 'むろ', 'THẤT · phòng', 9, '宀', 'Mái nhà ở trên, nơi người ta dừng lại bên dưới.'],
  ['急', 'キュウ', 'いそ-ぐ', 'CẤP · gấp, vội', 9, '心', 'Trái tim ở dưới — lòng nóng như lửa đốt.'],
  ['説', 'セツ・ゼイ', 'と-く', 'THUYẾT · giải thích, thuyết', 14, '言', 'Bộ ngôn (lời nói) — dùng lời để làm rõ.'],
  ['明', 'メイ・ミョウ', 'あか-るい、あ-ける', 'MINH · sáng', 8, '日', 'Mặt trời cạnh mặt trăng — sáng nhất.'],
  ['準', 'ジュン', '', 'CHUẨN · chuẩn, mức', 13, '水', 'Mặt nước phẳng dùng làm thước đo chuẩn.'],
  ['備', 'ビ', 'そな-える', 'BỊ · chuẩn bị, trang bị', 12, '人', 'Người đứng cạnh ống tên đã sẵn sàng.'],
  ['約', 'ヤク', '', 'ƯỚC · hẹn, khoảng chừng', 9, '糸', 'Sợi chỉ buộc lại — lời hẹn ràng buộc.'],
  ['束', 'ソク', 'たば', 'THÚC · bó, buộc', 7, '木', 'Cây bị buộc một vòng dây ở giữa.'],
  ['予', 'ヨ', '', 'DỰ · trước, dự', 4, '亅', 'Có trong 予約 (đặt trước), 予定 (dự định).'],
  ['験', 'ケン・ゲン', '', 'NGHIỆM · thử, kiểm nghiệm', 18, '馬', 'Có trong 試験 (kỳ thi), 経験 (kinh nghiệm).'],
  ['集', 'シュウ', 'あつ-まる、あつ-める', 'TẬP · tập hợp', 12, '隹', 'Đàn chim tụ trên cây.'],
  ['決', 'ケツ', 'き-める、き-まる', 'QUYẾT · quyết định', 7, '水', 'Nước phá bờ — một khi đã quyết thì không quay lại.'],
];
const KANJI_N5 = [
  ['午', 'ゴ', '', 'NGỌ · buổi trưa', 4, '十', 'Có trong 午前 (sáng) và 午後 (chiều).'],
  ['後', 'ゴ・コウ', 'うし-ろ、あと、のち', 'HẬU · sau', 9, '彳', 'Bước chân đi chậm phía sau.'],
  ['前', 'ゼン', 'まえ', 'TIỀN · trước', 9, '刀', 'Đối lập với 後: 前 ↔ 後.'],
  ['週', 'シュウ', '', 'CHU · tuần', 11, '辵', 'Bộ sước (đi) — một vòng đi hết bảy ngày.'],
  ['毎', 'マイ', '', 'MỖI · mỗi', 6, '毋', 'Có trong 毎日, 毎週, 毎朝.'],
  ['今', 'コン・キン', 'いま', 'KIM · bây giờ', 4, '人', 'Chú ý: 今日 đọc きょう, không đọc こんにち.'],
  ['何', 'カ', 'なに、なん', 'HÀ · cái gì', 7, '人', 'Người đứng cạnh chữ khả — người đang hỏi.'],
  ['半', 'ハン', 'なか-ば', 'BÁN · một nửa', 5, '十', 'Một nét dọc chia đôi — một nửa.'],
  ['分', 'ブン・フン・ブ', 'わ-ける', 'PHÂN · phút, phần', 4, '刀', 'Con dao chia đôi vật — phân chia.'],
  ['本', 'ホン', 'もと', 'BẢN · sách, gốc', 5, '木', 'Vạch đánh dấu ở gốc cây — cái gốc.'],
];

for (const [w, r] of [...VOCAB_N4, ...VOCAB_N3, ...VOCAB_N5]) {
  if (EXISTING_VOCAB.has(`${w}|${r}`)) throw new Error(`Từ vựng trùng với dữ liệu có sẵn: ${w}`);
}
for (const [g] of [...KANJI_N4, ...KANJI_N5]) {
  if (EXISTING_KANJI.has(g)) throw new Error(`Chữ Hán trùng với dữ liệu có sẵn: ${g}`);
}

let nextVocab = 20001;
const VOCABS = [
  ...VOCAB_N4.map((v) => ({ id: nextVocab++, level: LV.N4, v })),
  ...VOCAB_N3.map((v) => ({ id: nextVocab++, level: LV.N3, v })),
  ...VOCAB_N5.map((v) => ({ id: nextVocab++, level: LV.N5, v })),
];
let nextKanji = 20001;
const KANJIS = [
  ...KANJI_N4.map((k) => ({ id: nextKanji++, level: LV.N4, k })),
  ...KANJI_N5.map((k) => ({ id: nextKanji++, level: LV.N5, k })),
];

const DECKS = [
  { id: 20001, level: LV.N4, name: 'N4 · Từ vựng công việc & sinh hoạt', desc: '30 từ N4 hay gặp trong đề và trong đời sống công sở.',
    items: VOCABS.filter((x) => x.level === LV.N4).map((x) => ['VOCAB', x.id]) },
  { id: 20002, level: LV.N3, name: 'N3 · Từ vựng chủ đề xã hội', desc: '15 từ N3 xuất hiện nhiều trong bài đọc nghị luận.',
    items: VOCABS.filter((x) => x.level === LV.N3).map((x) => ['VOCAB', x.id]) },
  { id: 20003, level: LV.N4, name: 'N4 · 20 chữ Hán thường gặp', desc: 'Chữ Hán của các từ 会社・病院・説明・準備・約束…',
    items: KANJIS.filter((x) => x.level === LV.N4).map((x) => ['KANJI', x.id]) },
  { id: 20004, level: LV.N5, name: 'N5 · Từ vựng thời gian & địa điểm', desc: 'Bổ sung cho lộ trình N5: giờ giấc, nơi chốn, cặp từ trái nghĩa.',
    items: VOCABS.filter((x) => x.level === LV.N5).map((x) => ['VOCAB', x.id]) },
  { id: 20005, level: LV.N5, name: 'N5 · Chữ Hán chỉ thời gian', desc: '午・前・後・週・毎・今・何・半・分・本',
    items: KANJIS.filter((x) => x.level === LV.N5).map((x) => ['KANJI', x.id]) },
];

// ═══════════════════════════ Đề thi ═══════════════════════════

const ids = (bankId, n) => questionsOf(bankId).slice(0, n ?? undefined).map((q) => q.id);
const EXAMS = [
  { id: 20001, level: LV.N5, by: 'tt-gv-03', title: 'N5 · Luyện chữ Hán & từ vựng — Đề 1', minutes: 15, isPublic: true, shuffleO: true, questions: ids(20001), createdDays: 80 },
  { id: 20002, level: LV.N5, by: 'tt-gv-03', title: 'N5 · Luyện ngữ pháp — Đề 1', minutes: 15, isPublic: true, shuffleO: true, questions: ids(20002), createdDays: 80 },
  { id: 20003, level: LV.N5, by: 'tt-gv-03', title: 'N5 · Luyện đọc hiểu — Đề 1', minutes: 20, isPublic: true, questions: ids(20003), createdDays: 78 },
  { id: 20004, level: LV.N5, by: 'tt-gv-03', title: 'N5 · Đề thi thử 言語知識・読解', minutes: 60, isPublic: true, maxAttempts: 2, shuffleQ: true, shuffleO: true, createdDays: 60,
    sections: [
      { id: 20001, name: '言語知識（文字・語彙）', minutes: 20, questions: ids(20001) },
      { id: 20002, name: '言語知識（文法）・読解', minutes: 40, questions: [...ids(20002), ...ids(20003)] },
    ] },
  { id: 20005, level: LV.N4, by: 'tt-gv-02', title: 'N4 · Luyện chữ Hán & từ vựng', minutes: 15, isPublic: true, shuffleO: true, questions: ids(20004), createdDays: 76 },
  { id: 20006, level: LV.N4, by: 'tt-gv-02', title: 'N4 · Luyện ngữ pháp trọng tâm', minutes: 20, isPublic: true, shuffleO: true, questions: ids(20005), createdDays: 76 },
  { id: 20007, level: LV.N4, by: 'tt-gv-02', title: 'N4 · Luyện đọc hiểu — Thư & thông báo', minutes: 20, isPublic: true, questions: ids(20006), createdDays: 74 },
  { id: 20008, level: LV.N4, by: 'tt-gv-02', title: 'N4 · Đề thi thử 言語知識・読解', minutes: 80, isPublic: true, maxAttempts: 2, shuffleQ: true, shuffleO: true, createdDays: 55,
    sections: [
      { id: 20003, name: '言語知識（文字・語彙）', minutes: 25, questions: ids(20004) },
      { id: 20004, name: '言語知識（文法）・読解', minutes: 55, questions: [...ids(20005), ...ids(20006)] },
    ] },
  { id: 20009, level: LV.N3, by: 'tt-gv-01', title: 'N3 · Luyện chữ Hán & từ vựng', minutes: 15, isPublic: true, shuffleO: true, questions: ids(20007), createdDays: 70 },
  { id: 20010, level: LV.N3, by: 'tt-gv-01', title: 'N3 · Luyện ngữ pháp', minutes: 20, isPublic: true, shuffleO: true, questions: ids(20008), createdDays: 70 },
  { id: 20011, level: LV.N3, by: 'tt-gv-01', title: 'N3 · Luyện đọc hiểu — Bài nghị luận ngắn', minutes: 25, isPublic: true, questions: ids(20009), createdDays: 68 },
  { id: 20012, level: LV.N2, by: 'tt-gv-01', title: 'N2 · Ngữ pháp & từ vựng trọng tâm', minutes: 15, isPublic: true, shuffleO: true, questions: ids(20010), createdDays: 50 },
  // Bài xếp trình độ: 4 câu mỗi cấp, câu nào cũng thuộc ngân hàng đúng cấp.
  { id: 20013, level: LV.N5, by: 'tt-gv-01', title: 'Bài xếp trình độ đầu vào (N5 → N2)', minutes: 25, isPublic: true, placement: true, createdDays: 100,
    questions: [...ids(20001, 2), ...ids(20002, 2), ...ids(20004, 2), ...ids(20005, 2), ...ids(20007, 2), ...ids(20008, 2), ...ids(20010, 4)] },
  { id: 20014, level: LV.N4, by: 'tt-gv-02', title: 'Kiểm tra giữa khoá — Lớp N4 tối thứ 3·5', minutes: 20, isPublic: false, maxAttempts: 1, createdDays: 10,
    questions: [...ids(20005, 6), ...ids(20004, 4), ...ids(20006, 2)] },
  { id: 20015, level: LV.N3, by: 'tt-gv-01', title: 'Thi thử N3 — đợt tháng 12', minutes: 40, isPublic: false, maxAttempts: 1, allowReview: false, createdDays: 4,
    questions: [...ids(20007), ...ids(20008)] },
  { id: 20016, level: LV.N4, by: 'tt-gv-04', title: 'N4 · Đề ôn thể bị động (đang soạn)', minutes: 20, isPublic: false, createdDays: 3, questions: [] },
];
for (const e of EXAMS) {
  if (e.sections) e.questions = e.sections.flatMap((s) => s.questions);
  e.allowReview = e.allowReview ?? true;
}
const EXAM = Object.fromEntries(EXAMS.map((e) => [e.id, e]));
const QUESTION = Object.fromEntries(QUESTIONS.map((q) => [q.id, q]));

// Snapshot đáp án theo từng đề (id cố định để bài làm trỏ vào được).
let nextSnap = 300001;
const SNAP = {}; // `${examId}:${questionId}` → [{ id, original, text, correct, order }]
for (const e of EXAMS) {
  for (const qid of e.questions) {
    SNAP[`${e.id}:${qid}`] = QUESTION[qid].answers.map((a, i) => ({ id: nextSnap++, original: a.id, text: a.text, correct: a.correct, order: i + 1 }));
  }
}

// ═══════════════════════════ Phòng thi ═══════════════════════════

const ROOM_A_START = 6 * DAY + 140; // phút trước lúc chạy — 6 ngày trước, buổi tối
const ROOMS = [
  { id: 20001, name: 'Lớp luyện thi N4 — tối thứ 3·5', code: 'N4T35K', owner: 'tt-gv-02', level: LV.N4, capacity: 30, status: 'CLOSED',
    start: ROOM_A_START, end: ROOM_A_START - 20, exams: [20014], createdDays: 30,
    members: ['09', '10', '11', '12', '13', '14', '15', '16', '17', '02', '05', '18'] },
  { id: 20002, name: 'Thi thử N3 — đợt tháng 12', code: 'N3DEC6', owner: 'tt-gv-01', level: LV.N3, capacity: 40, status: 'OPEN',
    start: -5 * DAY, end: -5 * DAY - 40, exams: [20015], createdDays: 4,
    members: ['18', '19', '20', '21', '22', '23', '12', '24'] },
  { id: 20003, name: 'Lớp N5 cuối tuần — khoá mới', code: 'N5WKND', owner: 'tt-gv-03', level: LV.N5, capacity: 25, status: 'DRAFT',
    start: null, end: null, exams: [], createdDays: 1, members: [] },
];

// ═══════════════════════════ Lộ trình ôn tập ═══════════════════════════

const COURSES = [
  { id: 20001, level: LV.N5, author: 'tt-gv-03', status: 'PUBLISHED', reviewedDays: 70, createdDays: 85,
    title: 'Lộ trình N5 — Nền tảng trong 6 tuần',
    desc: 'Đi từ câu です đầu tiên tới đề thi thử N5: ngữ pháp cốt lõi, 20 chữ Hán chỉ thời gian, và kỹ năng đọc hiểu cơ bản.',
    lessons: [
      { type: 'GRAMMAR', minutes: 15, title: 'Chặng 1 · Câu です — khẳng định, phủ định, nghi vấn', content:
`CẤU TRÚC
N1 は N2 です。 — N1 là N2.
N1 は N2 じゃありません ／ ではありません。 — N1 không phải là N2.
N1 は N2 ですか。 — N1 có phải là N2 không?

VÍ DỤ
わたしは ベトナムじんです。 — Tôi là người Việt Nam.
リンさんは がくせいじゃありません。かいしゃいんです。 — Chị Linh không phải sinh viên, chị ấy là nhân viên công ty.
あの ひとは せんせいですか。 — Người kia là giáo viên phải không?
→ はい、そうです。／ いいえ、ちがいます。

GHI NHỚ
• は trong mẫu câu này đọc là "wa", không đọc "ha".
• か ở cuối câu biến câu thành câu hỏi. Không đảo trật tự từ như tiếng Anh.
• じゃありません dùng khi nói chuyện, ではありません trang trọng hơn, hay gặp trong văn viết.

LỖI NGƯỜI VIỆT HAY MẮC
Bỏ quên は: "わたし ベトナムじんです" nghe cụt. Tiếng Việt không có trợ từ chủ đề nên rất dễ quên — hãy tập nói trọn cả câu.` },
      { type: 'VOCAB', minutes: 20, deck: 20004, title: 'Chặng 2 · Từ vựng thời gian & địa điểm', content:
`Chặng này học bằng thẻ ghi nhớ.

CÁCH HỌC HIỆU QUẢ
1. Mỗi ngày mở bộ thẻ, ôn hết số thẻ đến hạn trước rồi mới học thẻ mới.
2. Đọc to cả từ và câu ví dụ. Nhớ từ trong câu thì dùng được, nhớ từ đứng một mình thì chỉ nhận ra được.
3. Chấm thật lòng: nhớ mang máng thì chọn "Khó", đừng chọn "Tốt". Hệ thống dựa vào đó để hẹn ngày ôn lại.

NHÓM TỪ TRỌNG TÂM
• Thời gian: ごぜん (buổi sáng), ごご (buổi chiều), せんげつ (tháng trước)
• Địa điểm: ぎんこう (ngân hàng), こうえん (công viên), としょかん (thư viện)
• Động từ đi cặp: あける ↔ しめる (mở ↔ đóng)
• Tính từ trái nghĩa: おもい ↔ かるい (nặng ↔ nhẹ)

Mẹo: học theo cặp trái nghĩa thì nhớ được gấp đôi mà chỉ tốn công một lần.` },
      { type: 'GRAMMAR', minutes: 20, exam: 20002, minScore: 60, title: 'Chặng 3 · Trợ từ を・に・で・へ', content:
`Trợ từ là "khớp nối" của câu tiếng Nhật. Sai trợ từ là câu đổi nghĩa hoặc vô nghĩa.

を — đánh dấu tân ngữ
パンを たべます。 — Ăn bánh mì.

に — thời điểm cụ thể, đích đến, người nhận
7じに おきます。 — Dậy lúc 7 giờ.
ともだちに てがみを かきます。 — Viết thư cho bạn.

で — nơi diễn ra hành động, phương tiện
としょかんで べんきょうします。 — Học ở thư viện.
でんしゃで いきます。 — Đi bằng tàu điện.

へ — hướng di chuyển (đọc là "e")
にほんへ いきます。 — Đi Nhật.

PHÂN BIỆT に VÀ で KHI CHỈ NƠI CHỐN
• に: nơi SỰ VẬT TỒN TẠI → へやに ねこが います。
• で: nơi HÀNH ĐỘNG XẢY RA → へやで ほんを よみます。
Mẹo: có động từ hành động (ăn, học, chơi…) thì gần như luôn là で.

KIỂM TRA CHẶNG
Làm đề "N5 · Luyện ngữ pháp — Đề 1" và đạt từ 60% để mở chặng tiếp theo.` },
      { type: 'KANJI', minutes: 20, deck: 20005, title: 'Chặng 4 · Chữ Hán chỉ thời gian', content:
`10 chữ Hán trong chặng này có mặt ở gần như mọi đề N5 vì chúng dùng để nói giờ giấc, ngày tháng.

BẢNG CHỮ
午 ゴ — trưa (NGỌ) → 午前 ごぜん buổi sáng, 午後 ごご buổi chiều
前 ゼン ／ まえ — trước (TIỀN) → 駅の前 えきのまえ trước nhà ga
後 ゴ ／ うしろ・あと — sau (HẬU)
週 シュウ — tuần (CHU) → 先週 せんしゅう tuần trước, 毎週 まいしゅう mỗi tuần
毎 マイ — mỗi (MỖI) → 毎日 まいにち mỗi ngày
今 コン ／ いま — bây giờ (KIM) → 今日 きょう hôm nay (cách đọc đặc biệt)
何 なに・なん — cái gì (HÀ) → 何時 なんじ mấy giờ
半 ハン — một nửa (BÁN) → 3時半 さんじはん ba giờ rưỡi
分 フン・プン — phút (PHÂN) → 5分 ごふん, 10分 じゅっぷん
本 ホン — sách, gốc (BẢN) → 日本 にほん Nhật Bản

CHÚ Ý BIẾN ÂM CỦA 分
Sau 2, 5, 7, 9 đọc ふん. Sau 1, 3, 4, 6, 8, 10 đọc ぷん: いっぷん, さんぷん, よんぷん, ろっぷん, はっぷん, じゅっぷん.` },
      { type: 'GRAMMAR', minutes: 20, title: 'Chặng 5 · Thể て — nhờ vả và nối câu', content:
`Thể て là dạng chia quan trọng nhất ở N5: nhờ vả, nối hành động, diễn tả việc đang làm đều bắt đầu từ nó.

CÁCH CHIA — ĐỘNG TỪ NHÓM 1
う・つ・る → って: かう → かって, まつ → まって, とる → とって
む・ぶ・ぬ → んで: よむ → よんで, あそぶ → あそんで
く → いて: かく → かいて (ngoại lệ: いく → いって)
ぐ → いで: いそぐ → いそいで
す → して: はなす → はなして

NHÓM 2: bỏ る thêm て → たべる → たべて
NHÓM 3: する → して, くる → きて

CÁCH DÙNG
1. Nhờ vả: Vて ください。 — まどを あけて ください。
2. Nối hành động theo thứ tự: あさ おきて、かおを あらって、がっこうへ いきます。
3. Đang làm: Vて います。 — いま、テレビを みて います。

LỖI HAY GẶP
Chia いく thành いいて. いく là ngoại lệ duy nhất của nhóm く → いって.` },
      { type: 'READING', minutes: 60, exam: 20004, minScore: 60, title: 'Chặng 6 · Thi thử cuối lộ trình', content:
`Chặng cuối là một đề thi thử theo đúng cấu trúc phần 言語知識 và 読解 của N5: hai phần, mỗi phần có đồng hồ riêng, hết giờ phần nào là khoá phần đó.

TRƯỚC KHI LÀM
• Chuẩn bị đủ 60 phút liền, không bị gián đoạn.
• Phần 1 (文字・語彙, 20 phút): đừng dừng quá lâu ở một câu chữ Hán. Không nhớ thì loại trừ rồi đi tiếp.
• Phần 2 (文法・読解, 40 phút): làm ngữ pháp nhanh để dành thời gian cho bài đọc.

KỸ NĂNG ĐỌC HIỂU N5
1. Đọc câu hỏi TRƯỚC rồi mới đọc đoạn văn — biết mình cần tìm gì thì đọc nhanh hơn.
2. Gạch chân giờ giấc, số lượng, địa điểm — đề N5 hay hỏi đúng những chi tiết này.
3. Đáp án đúng thường diễn đạt lại ý trong bài bằng từ khác. Đáp án lặp nguyên văn một cụm trong bài lại hay là bẫy.

Đạt từ 60% là hoàn thành lộ trình. Chưa đạt thì xem lại các câu sai trong sổ tay rồi làm lại — đề cho phép 2 lượt.` },
    ] },
  { id: 20002, level: LV.N4, author: 'tt-gv-02', status: 'PUBLISHED', reviewedDays: 65, createdDays: 80,
    title: 'Lộ trình N4 — Ngữ pháp trọng tâm',
    desc: 'Năm nhóm ngữ pháp chiếm phần lớn điểm 文法 của N4: thể ý chí, thể khả năng, điều kiện, bị động – sai khiến và kính ngữ.',
    lessons: [
      { type: 'GRAMMAR', minutes: 20, title: 'Chặng 1 · Thể ý chí — Vようと思っています', content:
`CÁCH CHIA
Nhóm 1: đổi đuôi sang hàng お ＋ う → いく → いこう, のむ → のもう, かえる → かえろう
Nhóm 2: bỏ る thêm よう → たべる → たべよう
Nhóm 3: する → しよう, くる → こよう

CÁCH DÙNG
1. Rủ rê thân mật: いっしょに かえろう。 — Cùng về đi.
2. Nói dự định: Vようと 思って います。
   来年、日本へ 留学しようと 思って います。 — Tôi đang định sang năm đi du học Nhật.

PHÂN BIỆT VỚI つもり
• Vようと思っています: dự định đang hình thành, còn có thể đổi.
• Vるつもりです: quyết tâm rõ hơn, đã tính kỹ.

BẪY TRONG ĐỀ
Đề hay đặt thể mệnh lệnh (行け, 飲め) cạnh thể ý chí (行こう, 飲もう) vì hai dạng này trông na ná. Nhìn nguyên âm cuối: お là ý chí, え là mệnh lệnh.` },
      { type: 'VOCAB', minutes: 20, deck: 20001, title: 'Chặng 2 · Từ vựng công việc & sinh hoạt', content:
`Chặng này học bằng bộ thẻ 30 từ N4.

NHÓM TỪ
• Công việc: 会議, 資料, 予約, 経験, 説明, 準備
• Cặp động từ dễ nhầm: 遅れる (trễ) ↔ 間に合う (kịp), 落とす (đánh rơi) ↔ 拾う (nhặt)
• Tính từ な: 丁寧, 親切, 複雑, 簡単, 必要
• Phó từ: 特に (đặc biệt là), 必ず (nhất định)

GỢI Ý
Đề 文字・語彙 của N4 hỏi cả CÁCH ĐỌC lẫn CÁCH DÙNG. Khi ôn thẻ, đọc to câu ví dụ và tự đặt thêm một câu của riêng mình.` },
      { type: 'GRAMMAR', minutes: 20, title: 'Chặng 3 · Thể khả năng — có thể ／ không thể', content:
`CÁCH CHIA
Nhóm 1: đổi đuôi sang hàng え ＋ る → よむ → よめる, かく → かける, はなす → はなせる
Nhóm 2: bỏ る thêm られる → たべる → たべられる, みる → みられる
Nhóm 3: する → できる, くる → こられる

TRỢ TỪ ĐỔI THEO
Tân ngữ thường chuyển từ を sang が:
日本語を 話す → 日本語が 話せる

VÍ DỤ
この 漢字が 読めますか。 — Bạn đọc được chữ Hán này không?
忙しくて、パーティーに 行けません。 — Bận quá nên không đi dự tiệc được.

LỖI HAY GẶP
Nhầm 見える ／ 聞こえる (tự nhiên thấy, tự nhiên nghe) với 見られる ／ 聞ける (có khả năng xem, nghe). Từ đây nhìn ra núi Phú Sĩ → 富士山が 見えます, không nói 見られます.` },
      { type: 'GRAMMAR', minutes: 25, title: 'Chặng 4 · Điều kiện たら・ば・と', content:
`BA CÁCH NÓI "NẾU"

たら — dùng được nhiều nhất, cả điều kiện lẫn "sau khi"
雨が 降ったら、行きません。 — Nếu mưa thì tôi không đi.
駅に 着いたら、電話して ください。 — Tới ga thì gọi cho tôi.

ば — nhấn vào điều kiện cần
安ければ 買います。 — Nếu rẻ thì mua.

と — kết quả tất yếu, lặp lại (máy móc, tự nhiên, chỉ đường)
この ボタンを 押すと、ドアが 開きます。 — Bấm nút này là cửa mở.

MẸO CHỌN NHANH TRONG ĐỀ
• Vế sau là lời nhờ, lời mời, ý chí (ください, ましょう, つもり) → dùng たら, không dùng と.
• Vế sau là quy luật, kết quả tự nhiên → と.
• Còn phân vân thì たら đúng trong nhiều trường hợp nhất.` },
      { type: 'GRAMMAR', minutes: 25, exam: 20006, minScore: 70, title: 'Chặng 5 · Kính ngữ cơ bản & kiểm tra cuối lộ trình', content:
`HAI NHÓM KÍNH NGỮ

尊敬語 — nâng hành động của NGƯỜI KHÁC
お＋V(bỏ ます)＋に なる: 部長は もう お帰りに なりました。
Động từ đặc biệt: いらっしゃる (đi, đến, ở), 召し上がる (ăn, uống), おっしゃる (nói)

謙譲語 — hạ hành động của CHÍNH MÌNH
お＋V(bỏ ます)＋する: わたしが お持ちします。
Động từ đặc biệt: 参る (đi, đến), いただく (ăn, nhận), 申す (nói)

CÁCH KHÔNG BAO GIỜ NHẦM
Hỏi: hành động này của AI? Của người trên → 尊敬語. Của mình → 謙譲語.

KIỂM TRA
Làm đề "N4 · Luyện ngữ pháp trọng tâm" và đạt từ 70% để hoàn thành lộ trình. Ngưỡng cao hơn các chặng trước vì đây là chặng tổng kết.` },
    ] },
  { id: 20003, level: LV.N3, author: 'tt-gv-01', status: 'PUBLISHED', reviewedDays: 40, createdDays: 60,
    title: 'Lộ trình N3 — Đọc hiểu không sợ bài dài',
    desc: 'Chiến thuật đọc câu hỏi trước, nhận ra ý chính qua liên từ, và luyện với bài nghị luận ngắn đúng dạng đề N3.',
    lessons: [
      { type: 'READING', minutes: 15, title: 'Chặng 1 · Đọc câu hỏi trước, đọc bài sau', content:
`Phần 読解 của N3 không thiếu thời gian nếu đọc có mục đích. Người mất điểm thường đọc kỹ từng chữ từ đầu tới cuối rồi mới xem câu hỏi.

QUY TRÌNH BA BƯỚC
1. Đọc câu hỏi và gạch chân từ khoá: 理由 (lý do), 筆者の考え (ý kiến tác giả), 何を (cái gì).
2. Đọc lướt đoạn văn, dừng lại ở chỗ chứa từ khoá hoặc liên từ đổi hướng (しかし, ところが).
3. Đối chiếu từng phương án với câu trong bài. Loại những phương án nói QUÁ MỨC bài viết (すべて, 必ず, 絶対).

CÂU HỎI "Ý KIẾN TÁC GIẢ"
Ý kiến thường nằm ở đoạn CUỐI, sau các cụm: 〜と思う, 〜べきだ, 〜ことが大切だ.` },
      { type: 'VOCAB', minutes: 20, deck: 20002, title: 'Chặng 2 · Từ vựng chủ đề xã hội', content:
`15 từ trong bộ thẻ này lặp lại rất nhiều trong bài đọc nghị luận N3: 増える ／ 減る, 調査, 関係, 習慣, 集中, 周り…

MẸO
Bài đọc N3 hay đưa số liệu khảo sát (調査によると…). Nắm chắc cặp 増える ↔ 減る và các từ chỉ tỉ lệ (割, 半分, 以上, 以下) là đã gỡ được nhiều câu hỏi chi tiết.` },
      { type: 'GRAMMAR', minutes: 20, title: 'Chặng 3 · Liên từ và mạch văn', content:
`Liên từ cho biết câu sau đi CÙNG hướng hay NGƯỢC hướng với câu trước — biết điều này là đoán được ý chính mà chưa cần hiểu hết từng chữ.

NGƯỢC HƯỚNG — ý quan trọng thường nằm SAU
しかし ／ ところが ／ でも
便利だ。しかし、使いすぎると 危ない。 → tác giả muốn nói về mặt nguy hiểm.

NGUYÊN NHÂN – KẾT QUẢ
そのため ／ だから ／ その結果

TÓM LẠI – DIỄN ĐẠT LẠI
つまり ／ 要するに → câu ngay sau thường chính là ý chính của cả đoạn.

THÊM Ý
それに ／ さらに ／ また

BÀI TẬP NHỎ
Mở bài "歩きスマホ" trong đề luyện đọc, khoanh tất cả liên từ, rồi thử đoán ý chính chỉ từ những câu đứng sau しかし và câu cuối bài.` },
      { type: 'READING', minutes: 25, exam: 20011, minScore: 60, title: 'Chặng 4 · Kiểm tra đọc hiểu', content:
`Làm đề "N3 · Luyện đọc hiểu — Bài nghị luận ngắn" và đạt từ 60% để hoàn thành lộ trình.

KHI LÀM
• Mỗi bài đọc có 2 câu hỏi, đoạn văn hiện một lần phía trên các câu.
• Áp dụng quy trình ba bước ở chặng 1.
• Làm xong, mở trang xem lại bài và đọc giải thích của từng câu sai — phần lớn lỗi ở N3 là chọn phương án "đúng một nửa".` },
    ] },
  { id: 20004, level: LV.N2, author: 'tt-gv-01', status: 'PENDING', createdDays: 6,
    title: 'Lộ trình N2 — Ngữ pháp văn viết',
    desc: 'Các mẫu ngữ pháp văn viết hay ra ở N2: nhượng bộ, phạm vi, kết quả.',
    lessons: [
      { type: 'GRAMMAR', minutes: 25, title: 'Chặng 1 · Nhượng bộ: にもかかわらず・ものの', content:
`にもかかわらず — mặc dù (kết quả trái với dự đoán, văn viết)
雨にもかかわらず、多くの 人が 集まった。

ものの — tuy … nhưng (thừa nhận vế trước, vế sau không như mong đợi)
免許は 取った ものの、まだ 運転に 自信が ない。

PHÂN BIỆT
• にもかかわらず nhấn vào sự NGẠC NHIÊN trước kết quả.
• ものの nhấn vào vế sau CHƯA TỐT, thường mang sắc thái tiếc nuối.` },
      { type: 'GRAMMAR', minutes: 25, title: 'Chặng 2 · Phạm vi: を問わず・に限らず', content:
`を問わず — bất kể, không phân biệt (thường đi với cặp đối lập)
年齢を問わず、誰でも 参加できます。

に限らず — không chỉ … mà cả
この アニメは 子どもに限らず、大人にも 人気が ある。

MẸO
Thấy 男女, 昼夜, 経験の有無 đứng trước ô trống → gần như chắc chắn là を問わず.` },
      { type: 'GRAMMAR', minutes: 25, title: 'Chặng 3 · Kết quả: かいがあって・あげく', content:
`かいがあって — công sức không uổng, kết quả TỐT
練習した かいがあって、優勝できた。

あげく — sau một thời gian dài (thường vất vả), kết cục XẤU hoặc bất đắc dĩ
さんざん 迷った あげく、何も 買わなかった。

BẪY
Hai mẫu đều nói về "kết quả sau một quá trình". Nhìn kết quả tốt hay xấu để chọn.` },
    ] },
  { id: 20005, level: LV.N4, author: 'tt-gv-02', status: 'REJECTED', reviewedDays: 3, createdDays: 12,
    reviewNote: 'Bài 2 và bài 3 đang trùng nội dung về 尊敬語. Tách rõ 尊敬語 và 謙譲語 thành hai bài, thêm ví dụ hội thoại nơi công sở rồi gửi lại.',
    title: 'N4 — Kính ngữ trong công việc',
    desc: 'Kính ngữ dùng hằng ngày ở công ty Nhật: nói với cấp trên, với khách hàng, qua điện thoại.',
    lessons: [
      { type: 'GRAMMAR', minutes: 20, title: 'Bài 1 · Vì sao người Nhật dùng kính ngữ', content:
`Kính ngữ không chỉ để tỏ ra lễ phép. Nó cho người nghe biết mình đang đứng ở vị trí nào so với họ: người trong công ty ／ người ngoài, cấp trên ／ cấp dưới.

BA LOẠI
• 丁寧語: です ／ ます
• 尊敬語: nâng người khác
• 謙譲語: hạ mình` },
      { type: 'GRAMMAR', minutes: 20, title: 'Bài 2 · 尊敬語 thường gặp', content:
`いらっしゃる ／ 召し上がる ／ おっしゃる ／ ご覧になる
部長は 会議室に いらっしゃいます。` },
      { type: 'GRAMMAR', minutes: 20, title: 'Bài 3 · Kính ngữ với cấp trên', content:
`社長が おっしゃいました。
お客様が いらっしゃいました。` },
    ] },
  { id: 20006, level: LV.N5, author: 'tt-gv-04', status: 'DRAFT', createdDays: 2,
    title: 'Lộ trình N5 — Luyện nghe cơ bản',
    desc: 'Nghe số đếm, giờ giấc và hội thoại ngắn hằng ngày. Đang soạn.',
    lessons: [
      { type: 'LISTENING', minutes: 15, title: 'Chặng 1 · Nghe số và giờ', content:
`(Đang soạn — chờ tải file nghe.)

MỤC TIÊU
Nghe và phân biệt 4時 (よじ) với 7時 (しちじ), 9時 (くじ) — ba giờ hay bị nghe nhầm nhất ở N5.` },
    ] },
];
let nextLesson = 20001;
for (const c of COURSES) for (const l of c.lessons) l.id = nextLesson++;
const COURSE = Object.fromEntries(COURSES.map((c) => [c.id, c]));

// ═══════════════════════════ Sinh bài làm ═══════════════════════════

const SUBMISSIONS = [];
let nextSub = 20001; let nextDetail = 500001;
const attemptsCount = {}; // `${examId}:${userId}` → n

/** Sinh một bài làm đã chấm. */
function makeSubmission(student, examId, startAgo, boost = 0, opts = {}) {
  const exam = EXAM[examId];
  const key = `${examId}:${student.id}`;
  const attempt = (attemptsCount[key] ?? 0) + 1;
  if (exam.maxAttempts && attempt > exam.maxAttempts) return null;
  attemptsCount[key] = attempt;

  const usedMinutes = opts.usedMinutes ?? Math.max(2, Math.round(exam.minutes * between(0.45, 0.95)));
  const auto = !opts.usedMinutes && rand() < 0.05;
  const used = auto ? exam.minutes : usedMinutes;
  const submittedAgo = startAgo - used;

  let total = 0; let correctCount = 0;
  const details = [];
  exam.questions.forEach((qid, idx) => {
    const q = QUESTION[qid];
    const levelGap = LEVEL_ORDER[q.levelId] - student.jlpt; // >0: câu dễ hơn trình độ, <0: khó hơn
    let p = student.ability + boost + (3 - q.difficulty) * 0.07 + (levelGap > 0 ? 0.06 * levelGap : 0.17 * levelGap);
    p = clamp(p, 0.04, 0.97);
    const snaps = SNAP[`${examId}:${qid}`];
    const blank = rand() < (auto ? 0.12 : 0.03);
    let chosen = null;
    if (!blank) {
      if (rand() < p) chosen = snaps.find((s) => s.correct);
      else chosen = pick(snaps.filter((s) => !s.correct));
    }
    const isCorrect = !!chosen?.correct;
    if (isCorrect) { total += 1; correctCount += 1; }
    details.push({
      id: nextDetail++, questionId: qid, snap: chosen, isCorrect,
      answeredAgo: startAgo - Math.max(0.5, (used * (idx + 1)) / (exam.questions.length + 1)),
    });
  });
  const sub = {
    id: nextSub++, examId, student, attempt, startAgo, submittedAgo, used, auto,
    total, correctCount, details, percent: exam.questions.length ? (total * 100) / exam.questions.length : 0,
  };
  SUBMISSIONS.push(sub);
  return sub;
}

/** Làm tới khi đạt ngưỡng (chặng lộ trình "đã qua" phải có bài kiểm tra đạt). */
function ensurePassed(student, examId, minPercent, startAgo) {
  const best = () => Math.max(-1, ...SUBMISSIONS.filter((s) => s.examId === examId && s.student.id === student.id).map((s) => s.percent));
  let ago = startAgo; let boost = 0.1;
  while (best() < minPercent) {
    const sub = makeSubmission(student, examId, ago, boost);
    if (!sub) {
      // Hết lượt mà vẫn chưa đạt: sửa lượt cuối thành đạt — ít tự nhiên hơn nhưng giữ đúng luật mở chặng.
      const last = SUBMISSIONS.filter((s) => s.examId === examId && s.student.id === student.id).at(-1);
      for (const d of last.details) {
        if (last.percent >= minPercent) break;
        if (!d.isCorrect) {
          d.snap = SNAP[`${examId}:${d.questionId}`].find((s) => s.correct);
          d.isCorrect = true; last.total += 1; last.correctCount += 1;
          last.percent = (last.total * 100) / EXAM[examId].questions.length;
        }
      }
      break;
    }
    ago -= between(0.5, 2) * DAY; boost += 0.12;
  }
}

const active = STUDENTS.filter((s) => s.id !== 'tt-hv-08');
const practiceByLevel = {
  5: [20001, 20002, 20003], 4: [20005, 20006, 20007], 3: [20009, 20010, 20011], 2: [20012],
};
const mockByLevel = { 5: 20004, 4: 20008 };

for (const st of active) {
  const created = st.createdDays * DAY;
  // Bài xếp trình độ ngay sau khi đăng ký
  if (rand() < 0.7) makeSubmission(st, 20013, created - between(60, 600));

  // Luyện đề đúng trình độ, có người làm lại để cải thiện điểm
  for (const examId of practiceByLevel[st.jlpt]) {
    if (rand() < 0.8) {
      let ago = between(8, Math.min(st.createdDays - 1, 34)) * DAY;
      const tries = 1 + Math.floor(rand() * 3);
      for (let t = 0; t < tries; t++) {
        makeSubmission(st, examId, ago, t * 0.07);
        ago -= between(1, 5) * DAY;
        if (ago < DAY / 2) break;
      }
    }
  }
  // Làm thử đề cấp dưới để lấy đà
  if (st.jlpt < 5 && rand() < 0.35) {
    makeSubmission(st, pick(practiceByLevel[st.jlpt + 1]), between(10, 30) * DAY);
  }
  // Đề thi thử
  if (mockByLevel[st.jlpt] && rand() < 0.6) makeSubmission(st, mockByLevel[st.jlpt], between(2, 12) * DAY);
  // N2 luyện thêm N3
  if (st.jlpt === 2) for (const e of practiceByLevel[3]) makeSubmission(st, e, between(15, 30) * DAY, 0.05);
}

// Bài kiểm tra của phòng A: mọi thành viên (trừ tài khoản sau này bị khoá — hôm đó vắng)
const roomA = ROOMS[0];
for (const n of roomA.members) {
  if (n === '17') continue;
  const st = S[n];
  const startedAgo = roomA.start - between(0.2, 2.5); // vào trong vài phút đầu
  const used = Math.round(between(9, 18));
  makeSubmission(st, 20014, startedAgo, 0, { usedMinutes: Math.min(used, Math.floor(startedAgo - roomA.end)) });
}

// ─── Ghi danh lộ trình + tiến độ ───
const ENROLLMENTS = []; const COMPLETIONS = [];
function enroll(st, courseId, doneCount, startedDaysAgo) {
  const course = COURSE[courseId];
  ENROLLMENTS.push({ user: st.id, course: courseId, startedAgo: startedDaysAgo * DAY });
  const lessons = course.lessons.slice(0, doneCount);
  lessons.forEach((l, i) => {
    const doneAgo = (startedDaysAgo - (i + 1) * between(1.5, 4)) * DAY;
    const at = Math.max(doneAgo, DAY * 0.3);
    if (l.exam) ensurePassed(st, l.exam, l.minScore, at + 90);
    COMPLETIONS.push({ user: st.id, lesson: l.id, doneAgo: at });
  });
}
for (const st of active) {
  const span = Math.min(st.createdDays - 2, 45);
  if (st.jlpt === 5) enroll(st, 20001, clamp(Math.floor(st.ability * 7.5), 1, 6), span);
  if (st.jlpt === 4) enroll(st, 20002, clamp(Math.floor(st.ability * 6.2), 1, 5), span);
  if (st.jlpt === 3) enroll(st, 20003, clamp(Math.floor(st.ability * 5), 1, 4), span);
  if (st.jlpt === 5 && st.ability > 0.7) enroll(st, 20002, 1, 8);
  if (st.jlpt === 2) enroll(st, 20003, 4, span);
}

// ─── Sổ tay câu sai: gom từ những câu làm sai ───
const MISTAKES = [];
{
  const byKey = {};
  for (const sub of [...SUBMISSIONS].sort((a, b) => b.submittedAgo - a.submittedAgo)) {
    for (const d of sub.details) {
      const k = `${sub.student.id}:${d.questionId}`;
      const m = byKey[k] ?? (byKey[k] = { user: sub.student.id, question: d.questionId, wrong: 0, streak: 0, lastWrongAgo: null });
      if (!d.isCorrect && d.snap) { m.wrong += 1; m.streak = 0; m.lastWrongAgo = sub.submittedAgo; }
      else if (d.isCorrect && m.wrong > 0) { m.streak += 1; }
    }
  }
  let nextMistake = 100001;
  for (const m of Object.values(byKey)) {
    if (m.wrong === 0) continue;
    const mastered = m.streak >= 2;
    MISTAKES.push({
      id: nextMistake++, ...m,
      nextReviewAgo: mastered ? null : m.lastWrongAgo - (m.streak + 1) * between(0.8, 3) * DAY,
      masteredAgo: mastered ? Math.max(DAY / 4, m.lastWrongAgo - 3 * DAY) : null,
    });
  }
}

// ─── Trạng thái thẻ ghi nhớ (SRS) ───
const CARDS = [];
{
  let nextCard = 100001;
  const itemsFor = (st) => {
    if (st.jlpt === 5) return [...DECKS[3].items, ...DECKS[4].items];
    if (st.jlpt === 4) return [...DECKS[0].items.slice(0, 22), ...DECKS[2].items.slice(0, 12)];
    return DECKS[1].items;
  };
  for (const st of active) {
    if (rand() < 0.25) continue; // không phải ai cũng học thẻ
    for (const [type, itemId] of itemsFor(st)) {
      if (rand() < 0.2) continue;
      const reps = Math.floor(between(0, 7));
      const interval = reps === 0 ? 0 : reps === 1 ? 1 : reps === 2 ? 6 : Math.round(6 * Math.pow(2.3, reps - 2));
      const dueAgo = between(-12, 3) * DAY; // âm = còn hạn trong tương lai
      CARDS.push({
        id: nextCard++, user: st.id, type, item: itemId,
        ease: clamp(2.5 + between(-0.7, 0.3), 1.3, 2.8).toFixed(2), interval, reps,
        lapses: reps > 2 && rand() < 0.3 ? 1 : 0, dueAgo, reviewedAgo: reps === 0 ? null : dueAgo + interval * DAY,
      });
    }
  }
}

// ─── Câu đánh dấu ───
const NOTES = ['Hay nhầm với cặp tự động từ ／ tha động từ.', 'Đọc lại giải thích trước hôm thi.', 'Đoán đúng nhưng chưa chắc — ôn lại.',
  'Mẫu này hay ra trong đề thật.', 'Nhớ: vế sau là lời nhờ thì dùng たら.', 'Bẫy đổi chủ thể, đọc kỹ câu hỏi.', null];
const BOOKMARKS = [];
{
  let nextBm = 100001; const seen = new Set();
  for (const sub of SUBMISSIONS.filter(() => rand() < 0.18)) {
    const d = pick(sub.details);
    const k = `${sub.student.id}:${d.questionId}`;
    if (seen.has(k)) continue;
    seen.add(k);
    BOOKMARKS.push({ id: nextBm++, user: sub.student.id, question: d.questionId, note: pick(NOTES), ago: sub.submittedAgo - 5 });
  }
}

// ─── Thông báo ───
const NOTIFS = [];
{
  let nextN = 100001;
  const add = (user, kind, subject, message, link, ago, read) => NOTIFS.push({ id: nextN++, user, kind, subject, message, link, ago, read });
  for (const n of roomA.members) {
    add(S[n].id, 'ROOM_STARTED', `Phòng "${roomA.name}" đã bắt đầu làm bài`, 'Người ra đề vừa bấm bắt đầu. Vào phòng để làm bài — đồng hồ đã chạy.', '/student/exams?tab=rooms', roomA.start, true);
    add(S[n].id, 'ROOM_ENDED', `Phòng "${roomA.name}" đã kết thúc`, 'Bảng xếp hạng và kết quả của cả phòng đã có.', '/student/exams?tab=rooms', roomA.end - 1, rand() < 0.6);
  }
  add('tt-gv-03', 'COURSE_APPROVED', `Lộ trình "${COURSES[0].title}" đã được duyệt`, 'Lộ trình đã xuất bản — học viên tìm thấy và ghi danh được rồi.', '/teacher/courses', COURSES[0].reviewedDays * DAY, true);
  add('tt-gv-02', 'COURSE_APPROVED', `Lộ trình "${COURSES[1].title}" đã được duyệt`, 'Lộ trình đã xuất bản — học viên tìm thấy và ghi danh được rồi.', '/teacher/courses', COURSES[1].reviewedDays * DAY, true);
  add('tt-gv-01', 'COURSE_APPROVED', `Lộ trình "${COURSES[2].title}" đã được duyệt`, 'Lộ trình đã xuất bản — học viên tìm thấy và ghi danh được rồi.', '/teacher/courses', COURSES[2].reviewedDays * DAY, true);
  add('tt-gv-02', 'COURSE_REJECTED', `Lộ trình "${COURSES[4].title}" bị trả lại`, `Lý do: ${COURSES[4].reviewNote}`, '/teacher/courses', COURSES[4].reviewedDays * DAY, false);
  const withCards = [...new Set(CARDS.filter((c) => c.dueAgo > 0).map((c) => c.user))];
  for (const u of withCards) {
    const due = CARDS.filter((c) => c.user === u && c.dueAgo > 0).length;
    add(u, 'CARDS_DUE', `Có ${due} thẻ đến hạn ôn hôm nay`, `Ôn đúng hạn thì nhớ lâu hơn nhiều so với ôn dồn. Dành vài phút cho ${due} thẻ đang chờ bạn.`, '/student/exams?tab=flashcards', between(60, 300), rand() < 0.3);
  }
}

// ═══════════════════════════ Xuất SQL ═══════════════════════════

comment('Tài khoản — mật khẩu chung: OnThi@2026');
insert('Users', ['UserID', 'full_name', 'email', 'password_hash', 'role', 'auth_provider', 'external_id', 'created_at', 'is_locked', 'locked_at', 'lock_reason'],
  ALL_USERS.map((u) => ({
    UserID: u.id, full_name: u.name, email: emailOf(u.name),
    password_hash: u.google ? null : PASSWORD_HASH, role: u.role,
    auth_provider: u.google ? 'GOOGLE' : 'LOCAL', external_id: u.google ? '108234519876543210987' : null,
    created_at: ago(u.createdDays * DAY), is_locked: !!u.locked,
    locked_at: u.locked ? ago(u.locked.daysAgo * DAY) : null, lock_reason: u.locked?.reason ?? null,
  })));

comment('Tag chủ điểm')
insert('Tags', ['TagID', 'TagName', 'CreatedAt'], TAG_NAMES.map((t) => ({ TagID: TAG[t], TagName: t, CreatedAt: ago(120 * DAY) })));

comment('Ngân hàng câu hỏi, bài đọc, câu hỏi, đáp án');
insert('QuestionBanks', ['BankID', 'Title', 'LevelID', 'TeacherID', 'CreatedAt'],
  BANKS.map((b) => ({ BankID: b.id, Title: b.title, LevelID: b.level, TeacherID: b.teacher, CreatedAt: ago(98 * DAY) })));
insert('ReadingPassages', ['PassageID', 'BankID', 'Title', 'Content', 'CreatedAt'],
  PASSAGES.map((p) => ({ PassageID: p.id, BankID: p.bankId, Title: p.title, Content: p.content, CreatedAt: ago(96 * DAY) })));
insert('Questions', ['QuestionID', 'BankID', 'Content', 'QuestionType', 'DifficultyLevel', 'IsAIGenerated', 'Explanation', 'CreatedAt', 'IsDeleted', 'Skill', 'PassageID'],
  QUESTIONS.map((q) => ({
    QuestionID: q.id, BankID: q.bankId, Content: q.content, QuestionType: q.type, DifficultyLevel: q.difficulty,
    IsAIGenerated: false, Explanation: q.explanation, CreatedAt: ago(q.createdDays * DAY), IsDeleted: false,
    Skill: q.skill, PassageID: q.passage?.id ?? null,
  })));
insert('Answers', ['AnswerID', 'QuestionID', 'AnswerContent', 'IsCorrect'],
  QUESTIONS.flatMap((q) => q.answers.map((a) => ({ AnswerID: a.id, QuestionID: q.id, AnswerContent: a.text, IsCorrect: a.correct }))));
insert('QuestionTags', ['QuestionID', 'TagID'],
  QUESTIONS.flatMap((q) => q.tags.map((t) => { if (!TAG[t]) throw new Error('Tag chưa khai báo: ' + t); return { QuestionID: q.id, TagID: TAG[t] }; })));

comment('Từ vựng, chữ Hán, bộ thẻ');
insert('VocabItems', ['VocabID', 'Word', 'Reading', 'Meaning', 'PartOfSpeech', 'LevelID', 'ExampleSentence', 'ExampleMeaning', 'CreatedAt'],
  VOCABS.map(({ id, level, v: [w, r, m, pos, ex, exm] }) => ({ VocabID: id, Word: w, Reading: r, Meaning: m, PartOfSpeech: pos, LevelID: level, ExampleSentence: ex, ExampleMeaning: exm, CreatedAt: ago(90 * DAY) })));
insert('KanjiItems', ['KanjiID', 'Glyph', 'Onyomi', 'Kunyomi', 'Meaning', 'StrokeCount', 'Radical', 'LevelID', 'Mnemonic', 'CreatedAt'],
  KANJIS.map(({ id, level, k: [g, on, kun, m, strokes, rad, mn] }) => ({ KanjiID: id, Glyph: g, Onyomi: on, Kunyomi: kun || null, Meaning: m, StrokeCount: strokes, Radical: rad, LevelID: level, Mnemonic: mn, CreatedAt: ago(90 * DAY) })));
insert('Decks', ['DeckID', 'Name', 'Description', 'LevelID', 'OwnerID', 'IsPublic', 'CreatedAt'],
  DECKS.map((d) => ({ DeckID: d.id, Name: d.name, Description: d.desc, LevelID: d.level, OwnerID: null, IsPublic: true, CreatedAt: ago(88 * DAY) })));
insert('DeckItems', ['DeckID', 'ItemType', 'ItemID', 'OrderNo'],
  DECKS.flatMap((d) => d.items.map(([type, id], i) => ({ DeckID: d.id, ItemType: type, ItemID: id, OrderNo: i + 1 }))));

comment('Đề thi, phần thi, câu trong đề (snapshot), đáp án snapshot');
insert('Exams', ['ExamID', 'LevelID', 'CreatedBy', 'Title', 'DurationMinutes', 'StartTime', 'EndTime', 'IsAdaptive', 'CreatedAt', 'MaxAttempts', 'AllowReview', 'IsPublic', 'JlptScoring', 'IsPlacement', 'ShuffleQuestions', 'ShuffleOptions'],
  EXAMS.map((e) => ({
    ExamID: e.id, LevelID: e.level, CreatedBy: e.by, Title: e.title, DurationMinutes: e.minutes, StartTime: null, EndTime: null,
    IsAdaptive: false, CreatedAt: ago(e.createdDays * DAY), MaxAttempts: e.maxAttempts ?? null, AllowReview: e.allowReview,
    // JlptScoring TẮT: bộ dữ liệu không có file nghe (聴解), mà bảng điểm JLPT thiếu phần nghe thì luôn ra "Trượt".
    IsPublic: e.isPublic, JlptScoring: false, IsPlacement: !!e.placement, ShuffleQuestions: !!e.shuffleQ, ShuffleOptions: !!e.shuffleO,
  })));
insert('ExamSections', ['SectionID', 'ExamID', 'Name', 'DurationMinutes', 'OrderNo', 'CreatedAt'],
  EXAMS.flatMap((e) => (e.sections ?? []).map((s, i) => ({ SectionID: s.id, ExamID: e.id, Name: s.name, DurationMinutes: s.minutes, OrderNo: i + 1, CreatedAt: ago(e.createdDays * DAY) }))));
insert('ExamQuestions', ['ExamID', 'QuestionID', 'QuestionOrder', 'Points', 'QuestionContent', 'QuestionType', 'DifficultyLevel', 'Explanation', 'SnapshotAt', 'SectionID', 'Skill', 'PassageID', 'PassageTitle', 'PassageContent'],
  EXAMS.flatMap((e) => e.questions.map((qid, i) => {
    const q = QUESTION[qid];
    const section = e.sections?.find((s) => s.questions.includes(qid));
    return {
      ExamID: e.id, QuestionID: qid, QuestionOrder: i + 1, Points: 1, QuestionContent: q.content, QuestionType: q.type,
      DifficultyLevel: q.difficulty, Explanation: q.explanation, SnapshotAt: ago(e.createdDays * DAY), SectionID: section?.id ?? null,
      Skill: q.skill, PassageID: q.passage?.id ?? null, PassageTitle: q.passage?.title ?? null, PassageContent: q.passage?.content ?? null,
    };
  })));
insert('ExamQuestionAnswers', ['SnapshotAnswerID', 'ExamID', 'QuestionID', 'OriginalAnswerID', 'AnswerContent', 'IsCorrect', 'AnswerOrder'],
  EXAMS.flatMap((e) => e.questions.flatMap((qid) => SNAP[`${e.id}:${qid}`].map((s) => ({
    SnapshotAnswerID: s.id, ExamID: e.id, QuestionID: qid, OriginalAnswerID: s.original, AnswerContent: s.text, IsCorrect: s.correct, AnswerOrder: s.order,
  })))));

comment('Phòng thi');
insert('Rooms', ['RoomID', 'Name', 'Code', 'OwnerID', 'LevelID', 'Capacity', 'JoinPolicy', 'Status', 'StartTime', 'EndTime', 'CreatedAt'],
  ROOMS.map((r) => ({ RoomID: r.id, Name: r.name, Code: r.code, OwnerID: r.owner, LevelID: r.level, Capacity: r.capacity, JoinPolicy: 'CODE', Status: r.status,
    StartTime: r.start == null ? null : ago(r.start), EndTime: r.end == null ? null : ago(r.end), CreatedAt: ago(r.createdDays * DAY) })));
insert('RoomExams', ['RoomID', 'ExamID', 'OrderNo', 'AttachedAt'],
  ROOMS.flatMap((r) => r.exams.map((e, i) => ({ RoomID: r.id, ExamID: e, OrderNo: i + 1, AttachedAt: ago(r.createdDays * DAY - 30) }))));
insert('RoomMembers', ['RoomID', 'UserID', 'SeatNo', 'Status', 'JoinedAt'],
  ROOMS.flatMap((r) => r.members.map((n, i) => ({ RoomID: r.id, UserID: S[n].id, SeatNo: i + 1, Status: 'ACTIVE', JoinedAt: ago(r.createdDays * DAY - 60 - i * 7) }))));

comment('Lộ trình ôn tập');
insert('Courses', ['CourseID', 'Title', 'Description', 'LevelID', 'AuthorID', 'Status', 'ReviewedBy', 'ReviewedAt', 'ReviewNote', 'CreatedAt', 'UpdatedAt'],
  COURSES.map((c) => ({
    CourseID: c.id, Title: c.title, Description: c.desc, LevelID: c.level, AuthorID: c.author, Status: c.status,
    ReviewedBy: c.reviewedDays ? ADMIN.id : null, ReviewedAt: c.reviewedDays ? ago(c.reviewedDays * DAY) : null, ReviewNote: c.reviewNote ?? null,
    CreatedAt: ago(c.createdDays * DAY), UpdatedAt: ago((c.reviewedDays ?? c.createdDays) * DAY + 60),
  })));
insert('CourseLessons', ['LessonID', 'CourseID', 'OrderNo', 'Title', 'LessonType', 'Content', 'EstimatedMinutes', 'DeckID', 'ExamID', 'CreatedAt', 'MinScorePercent'],
  COURSES.flatMap((c) => c.lessons.map((l, i) => ({
    LessonID: l.id, CourseID: c.id, OrderNo: i + 1, Title: l.title, LessonType: l.type, Content: l.content, EstimatedMinutes: l.minutes,
    DeckID: l.deck ?? null, ExamID: l.exam ?? null, CreatedAt: ago(c.createdDays * DAY), MinScorePercent: l.minScore ?? null,
  }))));

comment('Học viên: bài làm đã chấm');
insert('ExamSubmissions', ['SubmissionID', 'ExamID', 'StudentID', 'StartedAt', 'SubmittedAt', 'TotalScore', 'Status', 'AtRiskStatus', 'SyncToGoogleClassroom', 'ExpiresAt', 'LastActiveAt', 'AutoSubmitted', 'AttemptNumber'],
  SUBMISSIONS.map((s) => ({
    SubmissionID: s.id, ExamID: s.examId, StudentID: s.student.id, StartedAt: ago(s.startAgo), SubmittedAt: ago(s.submittedAgo),
    TotalScore: s.total.toFixed(2), Status: 'GRADED', AtRiskStatus: false, SyncToGoogleClassroom: false,
    ExpiresAt: ago(s.startAgo - EXAM[s.examId].minutes), LastActiveAt: ago(s.submittedAgo), AutoSubmitted: s.auto, AttemptNumber: s.attempt,
  })));
insert('SubmissionDetails', ['DetailID', 'SubmissionID', 'QuestionID', 'SelectedAnswerID', 'IsCorrect', 'AnsweredAt', 'ScoreEarned', 'SelectedSnapshotAnswerID', 'AudioPlays'],
  SUBMISSIONS.flatMap((s) => s.details.map((d) => ({
    DetailID: d.id, SubmissionID: s.id, QuestionID: d.questionId, SelectedAnswerID: d.snap?.original ?? null, IsCorrect: d.isCorrect,
    AnsweredAt: d.snap ? ago(d.answeredAgo) : null, ScoreEarned: d.isCorrect ? '1.00' : '0.00', SelectedSnapshotAnswerID: d.snap?.id ?? null, AudioPlays: 0,
  }))), 400);

comment('Học viên: ghi danh & tiến độ lộ trình');
insert('CourseEnrollments', ['UserID', 'CourseID', 'StartedAt'], ENROLLMENTS.map((e) => ({ UserID: e.user, CourseID: e.course, StartedAt: ago(e.startedAgo) })));
insert('LessonCompletions', ['UserID', 'LessonID', 'CompletedAt'], COMPLETIONS.map((c) => ({ UserID: c.user, LessonID: c.lesson, CompletedAt: ago(c.doneAgo) })));

comment('Học viên: sổ tay câu sai, thẻ ghi nhớ, câu đánh dấu, thông báo');
insert('MistakeEntries', ['MistakeID', 'UserID', 'QuestionID', 'WrongCount', 'CorrectStreak', 'LastWrongAt', 'NextReviewAt', 'MasteredAt', 'CreatedAt'],
  MISTAKES.map((m) => ({ MistakeID: m.id, UserID: m.user, QuestionID: m.question, WrongCount: m.wrong, CorrectStreak: m.streak,
    LastWrongAt: ago(m.lastWrongAgo), NextReviewAt: m.nextReviewAgo == null ? null : ago(m.nextReviewAgo), MasteredAt: m.masteredAgo == null ? null : ago(m.masteredAgo), CreatedAt: ago(m.lastWrongAgo) })));
insert('UserCardStates', ['CardStateID', 'UserID', 'ItemType', 'ItemID', 'EaseFactor', 'IntervalDays', 'Repetitions', 'Lapses', 'DueAt', 'LastReviewedAt', 'CreatedAt'],
  CARDS.map((c) => ({ CardStateID: c.id, UserID: c.user, ItemType: c.type, ItemID: c.item, EaseFactor: c.ease, IntervalDays: c.interval, Repetitions: c.reps, Lapses: c.lapses,
    DueAt: ago(c.dueAgo), LastReviewedAt: c.reviewedAgo == null ? null : ago(Math.max(c.reviewedAgo, 30)), CreatedAt: ago(30 * DAY) })));
insert('Bookmarks', ['BookmarkID', 'UserID', 'QuestionID', 'Note', 'CreatedAt', 'UpdatedAt'],
  BOOKMARKS.map((b) => ({ BookmarkID: b.id, UserID: b.user, QuestionID: b.question, Note: b.note, CreatedAt: ago(b.ago), UpdatedAt: ago(b.ago) })));
insert('Notifications', ['NotificationID', 'UserID', 'Channel', 'Subject', 'Message', 'Status', 'SentAt', 'CreatedAt', 'IsRead', 'Link', 'Kind'],
  NOTIFS.map((n) => ({ NotificationID: n.id, UserID: n.user, Channel: 'IN_APP', Subject: n.subject, Message: n.message, Status: 'SENT',
    SentAt: ago(n.ago), CreatedAt: ago(n.ago), IsRead: n.read, Link: n.link, Kind: n.kind })));

// ─── Ghi file ───
const header = `-- ═══════════════════════════════════════════════════════════════════
-- DỮ LIỆU THẬT CHO NỀN TẢNG ÔN THI JLPT — sinh bởi tools/seed/generate-realdata.mjs
-- ĐỪNG SỬA TAY FILE NÀY. Sửa nội dung trong script rồi chạy lại.
--
-- Nạp qua Liquibase (khuyên dùng): chạy app với LIQUIBASE_CONTEXTS=demo,realdata
-- Nạp bằng tay: chạy SAU KHI app đã khởi động ít nhất một lần (cần đủ schema v1.9.0).
-- Gỡ: chạy realdata-rollback.sql
--
-- Mật khẩu mọi tài khoản: OnThi@2026  —  email dạng ten.ho@${EMAIL_DOMAIN}
-- ═══════════════════════════════════════════════════════════════════
`;
let sql = header + '\n';
let pendingComment = '';
for (const st of statements) {
  if (st.comment) { pendingComment += `\n-- ── ${st.comment} ──\n`; continue; }
  if (st.sql.includes(';')) throw new Error('Câu lệnh chứa ";" bên trong: ' + st.sql.slice(0, 120));
  sql += pendingComment + st.sql + ';\n';
  pendingComment = '';
}

const rollback = `-- Gỡ sạch bộ dữ liệu thật (và mọi thứ app đã tạo ra DỰA TRÊN nó: bài làm trên đề của
-- bộ dữ liệu, đề/câu hỏi do tài khoản tt-* soạn thêm...). Không đụng dữ liệu khác.
-- Thứ tự xoá theo khoá ngoại: bảng con trước, bảng cha sau.
${[
  "DELETE FROM Notifications WHERE UserID LIKE 'tt-%'",
  "DELETE FROM Bookmarks WHERE UserID LIKE 'tt-%' OR QuestionID IN (SELECT QuestionID FROM Questions WHERE BankID BETWEEN 20001 AND 29999)",
  "DELETE FROM MistakeEntries WHERE UserID LIKE 'tt-%' OR QuestionID IN (SELECT QuestionID FROM Questions WHERE BankID BETWEEN 20001 AND 29999)",
  "DELETE FROM UserCardStates WHERE UserID LIKE 'tt-%' OR (ItemType = 'VOCAB' AND ItemID BETWEEN 20001 AND 29999) OR (ItemType = 'KANJI' AND ItemID BETWEEN 20001 AND 29999)",
  "DELETE FROM LessonCompletions WHERE UserID LIKE 'tt-%' OR LessonID IN (SELECT LessonID FROM CourseLessons WHERE CourseID BETWEEN 20001 AND 29999)",
  "DELETE FROM CourseEnrollments WHERE UserID LIKE 'tt-%' OR CourseID BETWEEN 20001 AND 29999",
  "DELETE FROM SubmissionDetails WHERE SubmissionID IN (SELECT SubmissionID FROM ExamSubmissions WHERE StudentID LIKE 'tt-%' OR ExamID BETWEEN 20001 AND 29999 OR ExamID IN (SELECT ExamID FROM Exams WHERE CreatedBy LIKE 'tt-%'))",
  "DELETE FROM SubmissionDetails WHERE QuestionID IN (SELECT QuestionID FROM Questions WHERE BankID BETWEEN 20001 AND 29999)",
  "DELETE FROM ExamSubmissions WHERE StudentID LIKE 'tt-%' OR ExamID BETWEEN 20001 AND 29999 OR ExamID IN (SELECT ExamID FROM Exams WHERE CreatedBy LIKE 'tt-%')",
  "DELETE FROM CourseLessons WHERE CourseID BETWEEN 20001 AND 29999 OR CourseID IN (SELECT CourseID FROM Courses WHERE AuthorID LIKE 'tt-%') OR ExamID BETWEEN 20001 AND 29999 OR DeckID BETWEEN 20001 AND 29999",
  "DELETE FROM Courses WHERE CourseID BETWEEN 20001 AND 29999 OR AuthorID LIKE 'tt-%'",
  "DELETE FROM RoomMembers WHERE UserID LIKE 'tt-%' OR RoomID BETWEEN 20001 AND 29999 OR RoomID IN (SELECT RoomID FROM Rooms WHERE OwnerID LIKE 'tt-%')",
  "DELETE FROM RoomExams WHERE RoomID BETWEEN 20001 AND 29999 OR ExamID BETWEEN 20001 AND 29999 OR RoomID IN (SELECT RoomID FROM Rooms WHERE OwnerID LIKE 'tt-%') OR ExamID IN (SELECT ExamID FROM Exams WHERE CreatedBy LIKE 'tt-%')",
  "DELETE FROM Rooms WHERE RoomID BETWEEN 20001 AND 29999 OR OwnerID LIKE 'tt-%'",
  "DELETE FROM ExamQuestionAnswers WHERE ExamID BETWEEN 20001 AND 29999 OR ExamID IN (SELECT ExamID FROM Exams WHERE CreatedBy LIKE 'tt-%') OR QuestionID IN (SELECT QuestionID FROM Questions WHERE BankID BETWEEN 20001 AND 29999)",
  "DELETE FROM ExamQuestions WHERE ExamID BETWEEN 20001 AND 29999 OR ExamID IN (SELECT ExamID FROM Exams WHERE CreatedBy LIKE 'tt-%') OR QuestionID IN (SELECT QuestionID FROM Questions WHERE BankID BETWEEN 20001 AND 29999)",
  "DELETE FROM ExamSections WHERE ExamID BETWEEN 20001 AND 29999",
  "DELETE FROM Exams WHERE ExamID BETWEEN 20001 AND 29999 OR CreatedBy LIKE 'tt-%'",
  "DELETE FROM DeckItems WHERE DeckID BETWEEN 20001 AND 29999 OR ItemID BETWEEN 20001 AND 29999",
  "DELETE FROM Decks WHERE DeckID BETWEEN 20001 AND 29999 OR OwnerID LIKE 'tt-%'",
  "DELETE FROM VocabItems WHERE VocabID BETWEEN 20001 AND 29999",
  "DELETE FROM KanjiItems WHERE KanjiID BETWEEN 20001 AND 29999",
  "DELETE FROM QuestionTags WHERE TagID BETWEEN 20001 AND 29999 OR QuestionID IN (SELECT QuestionID FROM Questions WHERE BankID BETWEEN 20001 AND 29999)",
  "DELETE FROM Answers WHERE QuestionID IN (SELECT QuestionID FROM Questions WHERE BankID BETWEEN 20001 AND 29999 OR BankID IN (SELECT BankID FROM QuestionBanks WHERE TeacherID LIKE 'tt-%'))",
  "DELETE FROM Questions WHERE BankID BETWEEN 20001 AND 29999 OR BankID IN (SELECT BankID FROM QuestionBanks WHERE TeacherID LIKE 'tt-%')",
  "DELETE FROM ReadingPassages WHERE BankID BETWEEN 20001 AND 29999 OR BankID IN (SELECT BankID FROM QuestionBanks WHERE TeacherID LIKE 'tt-%')",
  "DELETE FROM QuestionBanks WHERE BankID BETWEEN 20001 AND 29999 OR TeacherID LIKE 'tt-%'",
  "DELETE FROM Tags WHERE TagID BETWEEN 20001 AND 29999",
  "DELETE FROM Users WHERE UserID LIKE 'tt-%'",
].map((s) => s + ';').join('\n')}
`;

fs.mkdirSync(OUT_DIR, { recursive: true });
fs.writeFileSync(path.join(OUT_DIR, 'realdata.sql'), sql, 'utf8');
fs.writeFileSync(path.join(OUT_DIR, 'realdata-rollback.sql'), rollback, 'utf8');

const count = (arr) => arr.length;
console.log(JSON.stringify({
  users: count(ALL_USERS), students: count(STUDENTS), teachers: count(TEACHERS), tags: TAG_NAMES.length,
  banks: count(BANKS), passages: count(PASSAGES), questions: count(QUESTIONS), answers: QUESTIONS.reduce((n, q) => n + q.answers.length, 0),
  vocab: count(VOCABS), kanji: count(KANJIS), decks: count(DECKS), exams: count(EXAMS), rooms: count(ROOMS),
  courses: count(COURSES), lessons: COURSES.reduce((n, c) => n + c.lessons.length, 0),
  submissions: count(SUBMISSIONS), details: SUBMISSIONS.reduce((n, s) => n + s.details.length, 0),
  enrollments: count(ENROLLMENTS), completions: count(COMPLETIONS), mistakes: count(MISTAKES), cards: count(CARDS),
  bookmarks: count(BOOKMARKS), notifications: count(NOTIFS), sqlBytes: Buffer.byteLength(sql),
}, null, 1));
