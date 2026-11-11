#include <jni.h>
#include <dlfcn.h>
#include <stdint.h>
#include <string>
#include <vector>
#include <algorithm>
#include <cstring>

// Minimal libretro ABI definitions. Keep synchronized with libretro.h.
// These are the C ABI types and constants used by the frontend, not an emulator.
using retro_environment_t = bool (*)(unsigned, void*);
using retro_video_refresh_t = void (*)(const void*,unsigned,unsigned,size_t);
using retro_audio_sample_t = void (*)(int16_t,int16_t);
using retro_audio_sample_batch_t = size_t (*)(const int16_t*,size_t);
using retro_input_poll_t = void (*)();
using retro_input_state_t = int16_t (*)(unsigned,unsigned,unsigned,unsigned);
struct retro_game_info { const char* path; const void* data; size_t size; const char* meta; };
struct retro_system_info { const char* library_name; const char* library_version; const char* valid_extensions; bool need_fullpath; bool block_extract; };
struct retro_game_geometry { unsigned base_width,base_height,max_width,max_height; float aspect_ratio; };
struct retro_system_timing { double fps,sample_rate; };
struct retro_system_av_info { retro_game_geometry geometry; retro_system_timing timing; };
struct retro_variable { const char* key; const char* value; };
static constexpr unsigned SET_PIXEL_FORMAT=10, GET_SYSTEM_DIRECTORY=9, GET_SAVE_DIRECTORY=31;
static constexpr unsigned GET_VARIABLE=15, SET_VARIABLES=16, GET_VARIABLE_UPDATE=17;
static constexpr unsigned PIXEL_0RGB1555=0, PIXEL_XRGB8888=1, PIXEL_RGB565=2;
static constexpr unsigned DEVICE_JOYPAD=1;
static void* core=nullptr;
static std::vector<uint8_t> rom;
static std::vector<uint32_t> pixels(256*240,0xff000000);
static std::vector<int16_t> audio;
static unsigned frameWidth=256,frameHeight=240,format=PIXEL_0RGB1555;
static int buttonMask=0;
static std::string systemDir;

template<typename T> static T sym(const char* name) { return reinterpret_cast<T>(dlsym(core,name)); }
static void (*retro_set_environment)(retro_environment_t)=nullptr;
static void (*retro_set_video_refresh)(retro_video_refresh_t)=nullptr;
static void (*retro_set_audio_sample)(retro_audio_sample_t)=nullptr;
static void (*retro_set_audio_sample_batch)(retro_audio_sample_batch_t)=nullptr;
static void (*retro_set_input_poll)(retro_input_poll_t)=nullptr;
static void (*retro_set_input_state)(retro_input_state_t)=nullptr;
static void (*retro_init)()=nullptr;
static void (*retro_deinit)()=nullptr;
static void (*retro_get_system_info)(retro_system_info*)=nullptr;
static void (*retro_get_system_av_info)(retro_system_av_info*)=nullptr;
static bool (*retro_load_game)(const retro_game_info*)=nullptr;
static void (*retro_unload_game)()=nullptr;
static void (*retro_run)()=nullptr;
static void (*retro_set_controller_port_device)(unsigned,unsigned)=nullptr;
static size_t (*retro_serialize_size)()=nullptr;
static bool (*retro_serialize)(void*,size_t)=nullptr;
static bool (*retro_unserialize)(const void*,size_t)=nullptr;
static bool loaded=false;
static double sampleRate=44100.0, fps=60.0;

static bool environment(unsigned cmd,void* data) {
    if (cmd==SET_PIXEL_FORMAT && data) {
        unsigned requested=*reinterpret_cast<unsigned*>(data);
        if(requested<=PIXEL_RGB565) { format=requested;return true; }
        return false;
    }
    if ((cmd==GET_SYSTEM_DIRECTORY || cmd==GET_SAVE_DIRECTORY) && data) {
        *reinterpret_cast<const char**>(data)=systemDir.c_str();return true;
    }
    if(cmd==GET_VARIABLE && data) {
        auto* var=reinterpret_cast<retro_variable*>(data);
        var->value=nullptr;
        return true;
    }
    if(cmd==GET_VARIABLE_UPDATE && data) {
        *reinterpret_cast<bool*>(data)=false;return true;
    }
    if(cmd==SET_VARIABLES) return true;
    return false;
}
static void video(const void* src,unsigned w,unsigned h,size_t pitch) {
    if (!src || w==0 || h==0 || w>1024 || h>1024) return;
    frameWidth=w;frameHeight=h;
    pixels.resize(size_t(w)*h);
    const auto* bytes=static_cast<const uint8_t*>(src);
    for(unsigned y=0;y<h;y++) {
        for(unsigned x=0;x<w;x++) {
            uint32_t red=0,green=0,blue=0;
            if(format==PIXEL_XRGB8888) {
                uint32_t v;std::memcpy(&v,bytes+y*pitch+x*4,4);
                red=(v>>16)&255;green=(v>>8)&255;blue=v&255;
            } else {
                uint16_t v;std::memcpy(&v,bytes+y*pitch+x*2,2);
                if(format==PIXEL_RGB565) {
                    red=((v>>11)&31)*255/31; green=((v>>5)&63)*255/63; blue=(v&31)*255/31;
                } else {
                    red=((v>>10)&31)*255/31; green=((v>>5)&31)*255/31;blue=(v&31)*255/31;
                }
            }
            pixels[size_t(y)*w+x]=0xff000000|(red<<16)|(green<<8)|blue;
        }
    }
}
static void sample(int16_t l,int16_t r) {
    if(audio.size()+2<=16384) { audio.push_back(l);audio.push_back(r); }
}
static size_t samples(const int16_t* in,size_t frames) {
    if(in) {
        size_t count=std::min(frames*2,size_t(16384)-audio.size());
        audio.insert(audio.end(),in,in+count);
    }
    return frames;
}
static void poll() {}
static int16_t input(unsigned port,unsigned device,unsigned index,unsigned id) {
    if(port || device!=DEVICE_JOYPAD || index) return 0;
    // libretro: B=0, SELECT=2, START=3, UP=4, DOWN=5, LEFT=6, RIGHT=7, A=8.
    switch(id) {
        case 0:return (buttonMask>>5)&1;
        case 2:return (buttonMask>>7)&1;
        case 3:return (buttonMask>>6)&1;
        case 4:return (buttonMask>>0)&1;
        case 5:return (buttonMask>>1)&1;
        case 6:return (buttonMask>>2)&1;
        case 7:return (buttonMask>>3)&1;
        case 8:return (buttonMask>>4)&1;
        default:return 0;
    }
}
static void teardown() {
    if(loaded && retro_unload_game) retro_unload_game();
    loaded=false;
    if(core && retro_deinit) retro_deinit();
    if(core) dlclose(core);
    core=nullptr;rom.clear();audio.clear();
}
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_antidark_neslivingroom_NativeNes_open(JNIEnv* env,jclass,jstring path,jstring storage) {
    teardown();
    const char* lib=env->GetStringUTFChars(path,nullptr);
    core=dlopen(lib,RTLD_NOW|RTLD_LOCAL);
    env->ReleaseStringUTFChars(path,lib);
    if(!core) return JNI_FALSE;
    const char* s=env->GetStringUTFChars(storage,nullptr);
    systemDir=s;env->ReleaseStringUTFChars(storage,s);
    retro_set_environment=sym<decltype(retro_set_environment)>("retro_set_environment");
    retro_set_video_refresh=sym<decltype(retro_set_video_refresh)>("retro_set_video_refresh");
    retro_set_audio_sample=sym<decltype(retro_set_audio_sample)>("retro_set_audio_sample");
    retro_set_audio_sample_batch=sym<decltype(retro_set_audio_sample_batch)>("retro_set_audio_sample_batch");
    retro_set_input_poll=sym<decltype(retro_set_input_poll)>("retro_set_input_poll");
    retro_set_input_state=sym<decltype(retro_set_input_state)>("retro_set_input_state");
    retro_init=sym<decltype(retro_init)>("retro_init");
    retro_deinit=sym<decltype(retro_deinit)>("retro_deinit");
    retro_get_system_info=sym<decltype(retro_get_system_info)>("retro_get_system_info");
    retro_get_system_av_info=sym<decltype(retro_get_system_av_info)>("retro_get_system_av_info");
    retro_load_game=sym<decltype(retro_load_game)>("retro_load_game");
    retro_unload_game=sym<decltype(retro_unload_game)>("retro_unload_game");
    retro_run=sym<decltype(retro_run)>("retro_run");
    retro_set_controller_port_device=sym<decltype(retro_set_controller_port_device)>("retro_set_controller_port_device");
    retro_serialize_size=sym<decltype(retro_serialize_size)>("retro_serialize_size");
    retro_serialize=sym<decltype(retro_serialize)>("retro_serialize");
    retro_unserialize=sym<decltype(retro_unserialize)>("retro_unserialize");
    if(!retro_set_environment||!retro_set_video_refresh||!retro_set_audio_sample||
       !retro_set_audio_sample_batch||!retro_set_input_poll||!retro_set_input_state||
       !retro_init||!retro_deinit||!retro_get_system_info||!retro_get_system_av_info||
       !retro_load_game||!retro_unload_game||!retro_run) {teardown();return JNI_FALSE;}
    retro_set_environment(environment);
    retro_set_video_refresh(video);
    retro_set_audio_sample(sample);
    retro_set_audio_sample_batch(samples);
    retro_set_input_poll(poll);
    retro_set_input_state(input);
    retro_init();
    retro_system_info info{};retro_get_system_info(&info);
    // FCEUmm supports in-memory .nes files; reject cores that require a physical path.
    if(info.need_fullpath) {teardown();return JNI_FALSE;}
    return JNI_TRUE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_antidark_neslivingroom_NativeNes_load(JNIEnv* env,jclass,jbyteArray data) {
    if(!core||!data) return JNI_FALSE;
    jsize n=env->GetArrayLength(data);
    if(n<16||n>32*1024*1024) return JNI_FALSE;
    rom.resize(n);env->GetByteArrayRegion(data,0,n,reinterpret_cast<jbyte*>(rom.data()));
    retro_game_info info{"game.nes",rom.data(),rom.size(),nullptr};
    loaded=retro_load_game(&info);
    if(loaded) {
        if(retro_set_controller_port_device) retro_set_controller_port_device(0,DEVICE_JOYPAD);
        retro_system_av_info av{};retro_get_system_av_info(&av);
        if(av.timing.sample_rate>8000 && av.timing.sample_rate<192000) sampleRate=av.timing.sample_rate;
        if(av.timing.fps>40 && av.timing.fps<70) fps=av.timing.fps;
    }
    return loaded?JNI_TRUE:JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_dev_antidark_neslivingroom_NativeNes_runFrame(JNIEnv*,jclass,jint mask) {
    if(!loaded) return;
    buttonMask=mask;audio.clear();retro_run();
}
extern "C" JNIEXPORT jintArray JNICALL
Java_dev_antidark_neslivingroom_NativeNes_framePixels(JNIEnv* env,jclass) {
    jintArray arr=env->NewIntArray(static_cast<jsize>(pixels.size()));
    if(arr) env->SetIntArrayRegion(arr,0,static_cast<jsize>(pixels.size()),reinterpret_cast<const jint*>(pixels.data()));
    return arr;
}
extern "C" JNIEXPORT jint JNICALL
Java_dev_antidark_neslivingroom_NativeNes_frameWidth(JNIEnv*,jclass){return frameWidth;}
extern "C" JNIEXPORT jint JNICALL
Java_dev_antidark_neslivingroom_NativeNes_frameHeight(JNIEnv*,jclass){return frameHeight;}
extern "C" JNIEXPORT jshortArray JNICALL
Java_dev_antidark_neslivingroom_NativeNes_frameAudio(JNIEnv* env,jclass) {
    jshortArray arr=env->NewShortArray(static_cast<jsize>(audio.size()));
    if(arr&&!audio.empty()) env->SetShortArrayRegion(arr,0,static_cast<jsize>(audio.size()),audio.data());
    return arr;
}
extern "C" JNIEXPORT jint JNICALL
Java_dev_antidark_neslivingroom_NativeNes_sampleRate(JNIEnv*,jclass){return static_cast<int>(sampleRate);}
extern "C" JNIEXPORT jdouble JNICALL
Java_dev_antidark_neslivingroom_NativeNes_frameRate(JNIEnv*,jclass){return fps;}
extern "C" JNIEXPORT void JNICALL
Java_dev_antidark_neslivingroom_NativeNes_close(JNIEnv*,jclass){teardown();}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_antidark_neslivingroom_NativeNes_saveState(JNIEnv* env,jclass) {
    if(!loaded||!retro_serialize_size||!retro_serialize) return nullptr;
    size_t n=retro_serialize_size();
    if(n==0 || n>16*1024*1024) return nullptr;
    std::vector<uint8_t> state(n);
    if(!retro_serialize(state.data(),n)) return nullptr;
    jbyteArray arr=env->NewByteArray(static_cast<jsize>(n));
    if(arr) env->SetByteArrayRegion(arr,0,static_cast<jsize>(n),
                                    reinterpret_cast<const jbyte*>(state.data()));
    return arr;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_antidark_neslivingroom_NativeNes_loadState(JNIEnv* env,jclass,jbyteArray data) {
    if(!loaded||!data||!retro_unserialize) return JNI_FALSE;
    const jsize n=env->GetArrayLength(data);
    if(n<=0||n>16*1024*1024) return JNI_FALSE;
    std::vector<uint8_t> state(n);
    env->GetByteArrayRegion(data,0,n,reinterpret_cast<jbyte*>(state.data()));
    return retro_unserialize(state.data(),state.size())?JNI_TRUE:JNI_FALSE;
}
